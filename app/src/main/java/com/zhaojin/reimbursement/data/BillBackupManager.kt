package com.zhaojin.reimbursement.data

import android.content.Context
import android.net.Uri
import com.zhaojin.reimbursement.utils.BillPhotoStore
import com.zhaojin.reimbursement.utils.MiniImage
import com.zhaojin.reimbursement.utils.MiniSheet
import com.zhaojin.reimbursement.utils.MiniXlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/**
 * 账单数据备份管理器。
 *
 * 导出为**单个 xlsx**：照片直接内嵌在工作簿 drawing 层（「图片」列每格按张数
 * 横向铺 60px 锚点，D/E/F… 列），Excel/WPS 打开即可见图，无需额外附件包。
 * 导入自动识别三种文件：
 * - 单 xlsx（新格式）：图片取该行内嵌 drawing（Excel 编辑后图片丢失则按无图导入）；
 * - zip 备份包（旧版）：账单.xlsx + images/ 目录，按「图片」列文件名对位；
 * - 「捕账」xlsx：无图片列，按表格导入。
 * 表头按列名映射（顺序无关、多余列忽略），向前兼容未来加列。
 */
object BillBackupManager {

    const val SHEET_EXPENSE = "支出账单"
    const val SHEET_INCOME = "收入账单"
    const val ZIP_XLSX_ENTRY = "账单.xlsx"
    const val ZIP_IMAGES_DIR = "images/"
    const val PHOTO_HEADER = "图片"

    /** 「图片」列的 0 基列号（BILL_HEADERS 顺序固定），内嵌图片锚点从此列起横排 */
    private const val PHOTO_COL = 3

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
        /** 所在工作表名与物理行号（0 基，表头为 0），用于对位新格式内嵌图片 */
        val sheet: String = "",
        val row: Int = -1,
        /** 旧 zip 格式的图片文件名引用（images/ 下），其余格式为空 */
        val photos: List<String> = emptyList()
    )

    data class ParsedWorkbook(
        val bills: List<ParsedBill>,
        val badRows: Int
    )

    /** 解析后的备份：表格 + 旧 zip 图片字节 + 新格式内嵌图片 */
    data class ParsedBackup(
        val workbook: ParsedWorkbook,
        /** 旧 zip 备份包 images/ 下 文件名 → 字节 */
        val images: Map<String, ByteArray>,
        /** 新格式内嵌图片：表名 → 物理行 → 字节列表 */
        val embedded: Map<String, Map<Int, List<ByteArray>>>
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

    /**
     * 构建工作簿字节（纯函数，便于单测）：照片作为内嵌图片随表导出；
     * 「图片」列写张数文本，便于人工核对。
     */
    fun buildWorkbook(
        bills: List<BillEntity>,
        photosByBill: Map<Long, List<ByteArray>>
    ): ByteArray {
        fun formatTime(ts: Long) =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).format(OUT_FMT)

        fun sheetData(name: String, list: List<BillEntity>): MiniSheet {
            val sorted = list.sortedBy { it.timestamp }
            val rows = listOf(BILL_HEADERS) + sorted.map { b ->
                val n = photosByBill[b.id].orEmpty().size
                listOf<Any?>(formatTime(b.timestamp), b.title, b.amount, if (n > 0) "${n}张" else null)
            }
            // 每张照片一个锚点；同账单多张横向排开（D、E、F… 列），互不遮挡
            val images = sorted.flatMapIndexed { rowIdx, b ->
                photosByBill[b.id].orEmpty().mapIndexed { k, data ->
                    MiniImage(row = rowIdx + 1, col = PHOTO_COL + k, data = data)
                }
            }
            return MiniSheet(name, rows, images)
        }

        return MiniXlsx.write(
            listOf(
                sheetData(SHEET_EXPENSE, bills.filterNot { it.isIncome }),
                sheetData(SHEET_INCOME, bills.filter { it.isIncome })
            )
        )
    }

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        bills: List<BillEntity>,
        photosByBill: Map<Long, List<ByteArray>>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = buildWorkbook(bills, photosByBill)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("无法写入所选位置")
            bills.size
        }
    }

    // ------------------------------------------------------------------
    // 解析与导入
    // ------------------------------------------------------------------

    /**
     * 解析备份文件（纯函数，便于单测）：自动区分旧版 zip 备份包
     * （账单.xlsx + images/ 图片）与新格式单 xlsx（图片内嵌）。
     * 注意 xlsx 本身也是 zip（OOXML），因此 zip 判定依据是包内是否存在
     * 「账单.xlsx」条目，而不是文件头。
     */
    fun parseBackup(bytes: ByteArray): ParsedBackup {
        val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
        if (!isZip) {
            val wb = MiniXlsx.readWithImages(bytes)
            return ParsedBackup(parseSheets(wb.sheets), emptyMap(), wb.images)
        }
        val entries = unzip(bytes)
        val xlsxBytes = entries[ZIP_XLSX_ENTRY]
            ?: entries.entries.firstOrNull { it.key.endsWith(".xlsx") }?.value
            ?: // zip 容器但不含账单表格 → 是普通 xlsx 包（OOXML 本身即 zip），
            // 按带内嵌图片的 xlsx 解析，而不是丢图片的纯表格回退
            MiniXlsx.readWithImages(bytes).let {
                return ParsedBackup(parseSheets(it.sheets), emptyMap(), it.images)
            }
        val images = entries
            .filterKeys { it.startsWith(ZIP_IMAGES_DIR) && it.length > ZIP_IMAGES_DIR.length }
            .mapKeys { it.key.substringAfterLast('/') }
        return ParsedBackup(parseWorkbook(xlsxBytes), images, emptyMap())
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
    fun parseWorkbook(bytes: ByteArray): ParsedWorkbook = parseSheets(MiniXlsx.read(bytes))

    private fun parseSheets(sheets: List<MiniSheet>): ParsedWorkbook {
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
            sheet.rows.forEachIndexed { rowIdx, row ->
                if (rowIdx == 0) return@forEachIndexed
                if (row.all { it == null || (it is String && it.isBlank()) }) return@forEachIndexed
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
                        sheet = sheet.name,
                        row = rowIdx,
                        photos = parsePhotoRefs(str(row, col, PHOTO_HEADER))
                    )
                } catch (e: Exception) {
                    badRows++
                }
            }
        }
        return ParsedWorkbook(bills, badRows)
    }

    /**
     * 「images/a.jpg; images/b.jpg」→ [a.jpg, b.jpg]。
     * 只认带路径分隔的引用——新格式该列是「N张」计数文本，不能误当文件名。
     */
    private fun parsePhotoRefs(text: String?): List<String> =
        text?.split(';', '；', ',')
            ?.map { it.trim() }
            ?.filter { it.contains('/') }
            ?.map { it.substringAfterLast('/') }
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
        embedded: Map<String, Map<Int, List<ByteArray>>>,
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
            // 图片来源：新格式取该行内嵌 drawing；旧 zip 按「图片」列引用取 images/ 字节
            val photoSources = embedded[bill.sheet]?.get(bill.row).orEmpty()
                .ifEmpty { bill.photos.mapNotNull { images[it] } }
            photoSources.forEach { data ->
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
