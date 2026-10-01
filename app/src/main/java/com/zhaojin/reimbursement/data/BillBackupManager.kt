package com.zhaojin.reimbursement.data

import android.content.Context
import android.net.Uri
import com.zhaojin.reimbursement.utils.BillPhotoStore
import com.zhaojin.reimbursement.utils.MiniImage
import com.zhaojin.reimbursement.utils.MiniSheet
import com.zhaojin.reimbursement.utils.MiniTotal
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
import kotlin.math.roundToInt

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

    /**
     * 「图片」列的 0 基列号（BILL_HEADERS 顺序固定），内嵌图片锚点从此列起横排
     */
    private const val PHOTO_COL = 3

    /** 照片显示高度（96dpi 像素）：所有行统一，宽度按原图宽高比换算 */
    private const val PHOTO_DISPLAY_H_PX = 80

    /** 照片显示宽度上下限，避免极端宽高比撑爆版面 */
    private const val PHOTO_DISPLAY_W_MIN_PX = 40
    private const val PHOTO_DISPLAY_W_MAX_PX = 320

    /** 固定列宽（Excel 字符单位）：日期时间 / 标题 / 金额 */
    private val FIXED_COL_WIDTHS = linkedMapOf(0 to 20.0, 1 to 40.0, 2 to 20.0)

    /** 图片列宽（Excel 字符单位，固定大列宽便于电脑端查看） */
    private const val PHOTO_COL_WIDTH = 200.0

    /** 数据行高（磅，Excel 行高单位） */
    private const val DATA_ROW_HEIGHT_PT = 65.0

    /** 金额列货币格式：¥ + 千分位 + 两位小数 */
    private const val MONEY_FORMAT = "\"¥\"#,##0.00"

    /**
     * 待导出的照片：原始（归一化后）字节 + 原图像素宽高（用于按比例
     * 计算表格中的显示尺寸，避免拉伸变形）。
     */
    data class ExportPhoto(val data: ByteArray, val widthPx: Int, val heightPx: Int)

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
     * 构建工作簿字节（纯函数，便于单测）。版式：
     * - 固定列宽 日期时间20 / 标题40 / 金额20（货币 ¥ 两位小数），图片列固定 200；
     * - 数据行高统一 65 磅；照片按原图宽高比缩放、锚定格子原点；
     * - 表头浅灰底；每张表最后一行数据下有「总金额：」合计行（红字黄底）。
     */
    fun buildWorkbook(
        bills: List<BillEntity>,
        photosByBill: Map<Long, List<ExportPhoto>>
    ): ByteArray {
        fun formatTime(ts: Long) =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).format(OUT_FMT)

        fun displayWidthPx(p: ExportPhoto): Int {
            if (p.widthPx <= 0 || p.heightPx <= 0) return PHOTO_DISPLAY_H_PX
            return (PHOTO_DISPLAY_H_PX.toDouble() * p.widthPx / p.heightPx)
                .roundToInt().coerceIn(PHOTO_DISPLAY_W_MIN_PX, PHOTO_DISPLAY_W_MAX_PX)
        }

        fun sheetData(name: String, list: List<BillEntity>): MiniSheet {
            val sorted = list.sortedBy { it.timestamp }
            val rows = listOf(BILL_HEADERS) + sorted.map { b ->
                val n = photosByBill[b.id].orEmpty().size
                listOf<Any?>(formatTime(b.timestamp), b.title, b.amount, if (n > 0) "${n}张" else null)
            }
            // 照片锚点：同账单多张横向排开（D、E、F… 列），每张按宽高比定显示宽
            val images = sorted.flatMapIndexed { rowIdx, b ->
                photosByBill[b.id].orEmpty().mapIndexed { k, p ->
                    MiniImage(
                        row = rowIdx + 1, col = PHOTO_COL + k, data = p.data,
                        widthPx = displayWidthPx(p), heightPx = PHOTO_DISPLAY_H_PX
                    )
                }
            }
            // 列宽：固定三列 + 每个图片列固定 200；数据行高统一 65 磅
            val colWidths = LinkedHashMap(FIXED_COL_WIDTHS)
            val photoCount = sorted.maxOfOrNull { photosByBill[it.id].orEmpty().size } ?: 0
            for (k in 0 until photoCount) colWidths[PHOTO_COL + k] = PHOTO_COL_WIDTH
            val rowHeights = HashMap<Int, Double>()
            for (i in sorted.indices) rowHeights[i + 1] = DATA_ROW_HEIGHT_PT
            return MiniSheet(
                name = name,
                rows = rows,
                images = images,
                colWidths = colWidths,
                rowHeights = rowHeights,
                headerFill = true,
                colFormats = mapOf(2 to MONEY_FORMAT),
                total = MiniTotal(
                    label = "总金额：",
                    labelCol = 1,
                    valueCol = 2,
                    // 十进制累加避免二进制浮点尾巴（25.6+30.38 应为 55.98 而非 55.9800…04）
                    value = sorted.fold(java.math.BigDecimal.ZERO) { acc, b ->
                        acc + java.math.BigDecimal.valueOf(b.amount)
                    }.toDouble(),
                    formatCode = MONEY_FORMAT
                )
            )
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
        photosByBill: Map<Long, List<ExportPhoto>>
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
                // 合计行等非数据行（日期列为空）直接忽略，不计入坏行
                if (str(row, col, "日期时间").isNullOrBlank()) return@forEachIndexed
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
