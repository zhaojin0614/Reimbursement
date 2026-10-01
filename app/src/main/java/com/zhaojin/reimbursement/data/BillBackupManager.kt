package com.zhaojin.reimbursement.data

import android.content.Context
import android.net.Uri
import com.zhaojin.reimbursement.utils.BillPhotoStore
import com.zhaojin.reimbursement.utils.MiniSheet
import com.zhaojin.reimbursement.utils.MiniXlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 账单数据备份管理器。
 *
 * 备份为 zip 包（图片随包导出）：
 * ```
 * 维修报销_备份_YYYY-MM-DD.zip
 * ├─ 账单.xlsx     支出/收入两张表，列：日期时间|标题|金额|图片
 * │                「图片」列 = images/ 下文件名引用，多张用「; 」分隔
 * └─ images/       全部照片原图
 * ```
 * 导入自动识别三种文件：新版 zip（含图）、本 app 旧版 xlsx、「捕账」xlsx
 * （后两者无图片，按表格导入）；识别依据是 zip 文件头（PK）。
 * 表头按列名映射（顺序无关、多余列忽略），向前兼容未来加列。
 */
object BillBackupManager {

    const val SHEET_EXPENSE = "支出账单"
    const val SHEET_INCOME = "收入账单"
    const val ZIP_XLSX_ENTRY = "账单.xlsx"
    const val ZIP_IMAGES_DIR = "images/"
    const val PHOTO_HEADER = "图片"

    private val OUT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 导入可接受的日期写法（含 Excel 编辑后常见的斜杠变体） */
    private val IN_DATETIME_PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm"
    )
    private val IN_DATE_ONLY_PATTERNS = listOf("yyyy-MM-dd", "yyyy/MM/dd")

    private val BILL_HEADERS = listOf("日期时间", "标题", "金额", PHOTO_HEADER)

    data class ParsedBill(
        val amount: Double,
        val title: String,
        val isIncome: Boolean,
        val timestamp: Long,
        /** 引用的图片文件名（zip 包 images/ 下），旧格式无图则为空 */
        val photos: List<String> = emptyList()
    )

    data class ParsedWorkbook(
        val bills: List<ParsedBill>,
        val badRows: Int
    )

    /** 解析后的备份：表格 + 图片字节（zip 来源才有图片） */
    data class ParsedBackup(
        val workbook: ParsedWorkbook,
        val images: Map<String, ByteArray>
    )

    data class BackupWriteResult(
        val total: Int,
        val inserted: Int,
        val skipped: Int,
        val failed: Int,
        val photos: Int = 0
    ) {
        fun summary(): String = buildString {
            append("新增 $inserted 笔")
            if (photos > 0) append("、图片 $photos 张")
            append("、跳过 $skipped 笔、失败 $failed 笔")
        }
    }

    // ------------------------------------------------------------------
    // 导出
    // ------------------------------------------------------------------

    /** 构建工作簿字节（纯函数，便于单测）；图片列引用 images/ 下文件名 */
    fun buildWorkbook(
        bills: List<BillEntity>,
        photosByBill: Map<Long, List<String>>
    ): ByteArray {
        fun formatTime(ts: Long) =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).format(OUT_FMT)

        fun billRows(list: List<BillEntity>): List<List<Any?>> =
            listOf(BILL_HEADERS) + list.sortedBy { it.timestamp }.map { b ->
                val photos = photosByBill[b.id].orEmpty()
                listOf<Any?>(
                    formatTime(b.timestamp), b.title, b.amount,
                    photos.joinToString("; ") { "$ZIP_IMAGES_DIR$it" }
                )
            }

        return MiniXlsx.write(
            listOf(
                MiniSheet(SHEET_EXPENSE, billRows(bills.filterNot { it.isIncome })),
                MiniSheet(SHEET_INCOME, billRows(bills.filter { it.isIncome }))
            )
        )
    }

    /** 打包 zip：账单.xlsx + images/ 全部原图（读不到的图片跳过） */
    fun buildZip(
        bills: List<BillEntity>,
        photosByBill: Map<Long, List<String>>,
        readImage: (String) -> ByteArray?
    ): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ZIP_XLSX_ENTRY))
            zip.write(buildWorkbook(bills, photosByBill))
            zip.closeEntry()
            photosByBill.values.flatten().distinct().forEach { name ->
                val data = readImage(name) ?: return@forEach
                zip.putNextEntry(ZipEntry("$ZIP_IMAGES_DIR$name"))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        bills: List<BillEntity>,
        photosByBill: Map<Long, List<String>>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = buildZip(bills, photosByBill) { name ->
                BillPhotoStore.fileFor(context, name).takeIf { it.exists() }?.readBytes()
            }
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("无法写入所选位置")
            bills.size
        }
    }

    // ------------------------------------------------------------------
    // 解析与导入
    // ------------------------------------------------------------------

    /**
     * 解析备份文件（纯函数，便于单测）：自动区分新版 zip 备份包
     * （账单.xlsx + images/ 图片）与纯 xlsx（本 app 旧版/捕账导出，无图片）。
     * 注意 xlsx 本身也是 zip（OOXML），因此判定依据是包内是否存在
     * 「账单.xlsx」条目，而不是文件头。
     */
    fun parseBackup(bytes: ByteArray): ParsedBackup {
        val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) return ParsedBackup(parseWorkbook(bytes), emptyMap())
        val entries = unzip(bytes)
        val xlsxBytes = entries[ZIP_XLSX_ENTRY]
            ?: entries.entries.firstOrNull { it.key.endsWith(".xlsx") }?.value
            ?: // zip 但不含账单表格 → 不是本 app 备份包，按普通 xlsx 解析
            return ParsedBackup(parseWorkbook(bytes), emptyMap())
        val images = entries
            .filterKeys { it.startsWith(ZIP_IMAGES_DIR) && it.length > ZIP_IMAGES_DIR.length }
            .mapKeys { it.key.substringAfterLast('/') }
        return ParsedBackup(parseWorkbook(xlsxBytes), images)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    /** 解析表格（纯函数，便于单测）；结构缺失抛 IllegalArgumentException。 */
    fun parseWorkbook(bytes: ByteArray): ParsedWorkbook {
        val sheets = MiniXlsx.read(bytes)
        fun find(name: String) = sheets.firstOrNull { it.name == name }
        val expense = find(SHEET_EXPENSE)
        val income = find(SHEET_INCOME)
        if (expense == null && income == null) {
            throw IllegalArgumentException("未找到「$SHEET_EXPENSE」或「$SHEET_INCOME」工作表")
        }

        val bills = ArrayList<ParsedBill>()
        var badRows = 0
        listOf(expense to false, income to true).forEach { (sheet, isIncome) ->
            sheet ?: return@forEach
            // 必需列不含「图片」：旧版/捕账格式没有该列也能导入（解析为无图）
            val col = headerMap(sheet.rows.firstOrNull(), BILL_HEADERS, listOf("日期时间", "标题", "金额"), sheet.name)
            sheet.rows.drop(1).forEach { row ->
                if (row.all { it == null || (it is String && it.isBlank()) }) return@forEach
                try {
                    val timestamp = parseTimestamp(str(row, col, "日期时间") ?: throw IllegalStateException("缺少日期"))
                        ?: throw IllegalStateException("日期格式无法识别")
                    val amount = str(row, col, "金额")?.toDoubleOrNull()
                        ?.takeIf { it > 0 } ?: throw IllegalStateException("金额无效")
                    bills += ParsedBill(
                        amount = amount,
                        title = str(row, col, "标题") ?: "",
                        isIncome = isIncome,
                        timestamp = timestamp,
                        photos = parsePhotoRefs(str(row, col, PHOTO_HEADER))
                    )
                } catch (e: Exception) {
                    badRows++
                }
            }
        }
        return ParsedWorkbook(bills, badRows)
    }

    /** 「images/a.jpg; images/b.jpg」→ [a.jpg, b.jpg]；空/无引用返回空列表 */
    private fun parsePhotoRefs(text: String?): List<String> =
        text?.split(';', '；', ',')
            ?.map { it.trim().substringAfterLast('/') }
            ?.filter { it.isNotBlank() && it != "无" }
            .orEmpty()

    /**
     * 将解析出的备份写入库。overwrite=false 合并（账单按 时间+金额+标题+类型
     * 指纹去重，已存在的账单不重复导入图片）；true 恢复覆盖（清空账单、
     * 图片记录和图片文件后按备份重建）。
     */
    suspend fun importBackup(
        context: Context,
        billDao: BillDao,
        photoDao: BillPhotoDao,
        parsed: ParsedWorkbook,
        images: Map<String, ByteArray>,
        overwrite: Boolean
    ): BackupWriteResult = withContext(Dispatchers.IO) {
        if (overwrite) {
            billDao.deleteAll()
            photoDao.deleteAll()
            BillPhotoStore.clearAll(context)
        }
        val existingKeys = if (overwrite) HashSet<String>() else HashSet(
            billDao.getAllBillsOnce()
                .map { fingerprint(it.timestamp, it.amount, it.title, it.isIncome) }
        )
        var inserted = 0
        var skipped = 0
        var photoCount = 0
        parsed.bills.forEach { bill ->
            val key = fingerprint(bill.timestamp, bill.amount, bill.title, bill.isIncome)
            if (key in existingKeys) {
                skipped++
                return@forEach
            }
            existingKeys.add(key)
            val billId = billDao.insert(
                BillEntity(amount = bill.amount, title = bill.title, isIncome = bill.isIncome, timestamp = bill.timestamp)
            )
            if (billId <= 0) {
                skipped++
                return@forEach
            }
            inserted++
            bill.photos.forEach { ref ->
                val data = images[ref] ?: return@forEach
                val newName = BillPhotoStore.saveBytes(context, data) ?: return@forEach
                photoDao.insert(
                    BillPhotoEntity(billId = billId, fileName = newName, createdAt = bill.timestamp)
                )
                photoCount++
            }
        }
        BackupWriteResult(
            total = parsed.bills.size,
            inserted = inserted,
            skipped = skipped,
            failed = parsed.badRows,
            photos = photoCount
        )
    }

    private fun fingerprint(timestamp: Long, amount: Double, title: String, isIncome: Boolean): String =
        "$timestamp|$amount|$title|${if (isIncome) 1 else 0}"

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    /** 首行表头 → 列号映射；缺失必需列时抛出并指明所在 sheet。 */
    private fun headerMap(
        headerRow: List<Any?>?,
        allHeaders: List<String>,
        required: List<String>,
        sheetName: String
    ): Map<String, Int> {
        val map = HashMap<String, Int>()
        headerRow?.forEachIndexed { index, text ->
            text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { map.putIfAbsent(it, index) }
        }
        val missing = required.filter { it !in map }
        if (missing.isNotEmpty()) {
            throw IllegalArgumentException("工作表「$sheetName」缺少列：${missing.joinToString("、")}")
        }
        return map
    }

    private fun str(row: List<Any?>, col: Map<String, Int>, header: String): String? =
        col[header]?.let { row.getOrNull(it) }?.toString()?.trim()

    /** 解析导入日期；无法识别返回 null（由调用方按坏行计数） */
    private fun parseTimestamp(text: String): Long? {
        val clean = text.trim()
        for (pattern in IN_DATETIME_PATTERNS) {
            try {
                return LocalDateTime.parse(clean, DateTimeFormatter.ofPattern(pattern))
                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) { /* 尝试下一个格式 */ }
        }
        for (pattern in IN_DATE_ONLY_PATTERNS) {
            try {
                return LocalDate.parse(clean, DateTimeFormatter.ofPattern(pattern))
                    .atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (e: Exception) { /* 尝试下一个格式 */ }
        }
        return null
    }
}
