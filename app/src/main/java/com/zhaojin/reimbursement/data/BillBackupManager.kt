package com.zhaojin.reimbursement.data

import android.content.Context
import com.zhaojin.reimbursement.utils.BillPhotoStore
import com.zhaojin.reimbursement.utils.MiniImage
import com.zhaojin.reimbursement.utils.MiniSheet
import com.zhaojin.reimbursement.utils.MiniTotal
import com.zhaojin.reimbursement.utils.MiniXlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
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
 * 导出为**单个 xlsx**：仅一张「维修报销账单」表，照片直接内嵌在工作簿
 * drawing 层（「图片」列每格按张数横向铺开），Excel/WPS 打开即可见图。
 * 导入自动识别：
 * - 新格式：单张「维修报销账单」表（图片内嵌 drawing）；
 * - 旧版 zip 备份包（账单.xlsx + images/）：按「支出账单」表导入，
 *   「收入账单」表（收入功能已移除）忽略；
 * - 「捕账」xlsx：无图片列，按表格导入。
 * 表头按列名映射（顺序无关、多余列忽略），向前兼容未来加列。
 */
object BillBackupManager {

    /** 新版唯一工作表名 */
    const val SHEET_MAIN = "维修报销账单"

    /** 旧版工作表名（导入兼容） */
    const val SHEET_EXPENSE = "支出账单"
    const val SHEET_INCOME = "收入账单"
    const val ZIP_XLSX_ENTRY = "账单.xlsx"
    const val ZIP_IMAGES_DIR = "images/"
    const val PHOTO_HEADER = "图片"

    /**
     * 「图片」列的 0 基列号（BILL_HEADERS 顺序固定），内嵌图片锚点从此列起横排
     */
    private const val PHOTO_COL = 5

    /** 照片显示高度（96dpi 像素）：所有行统一，宽度按原图宽高比换算 */
    private const val PHOTO_DISPLAY_H_PX = 80

    /** 照片显示宽度上下限，避免极端宽高比撑爆版面 */
    private const val PHOTO_DISPLAY_W_MIN_PX = 40
    private const val PHOTO_DISPLAY_W_MAX_PX = 320

    /** 固定列宽（Excel 字符单位）：日期时间 / 驾驶员 / 车牌号 / 内容 / 金额 */
    private val FIXED_COL_WIDTHS = linkedMapOf(0 to 20.0, 1 to 20.0, 2 to 20.0, 3 to 40.0, 4 to 20.0)

    /** 数据行高（磅，Excel 行高单位） */
    private const val DATA_ROW_HEIGHT_PT = 65.0

    /** 金额列货币格式：¥ + 千分位 + 两位小数 */
    private const val MONEY_FORMAT = "\"¥\"#,##0.00"

    /**
     * 待导出的照片：原始（归一化后）字节 + 原图像素宽高（用于按比例
     * 计算表格中的显示尺寸，避免拉伸变形）。
     */
    data class ExportPhoto(val data: ByteArray, val widthPx: Int, val heightPx: Int)

    /** 96dpi 像素宽 → Excel 列宽字符单位（Calibri 11，MDW=7，标准公式 (px-5)/7） */
    internal fun colWidthChars(px: Int): Double =
        kotlin.math.round((px - 5) / 7.0 * 100) / 100

    private val OUT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 排序键：按日（本地时区）升序，同一天内按添加顺序（id）升序 */
    private fun dayOf(bill: BillEntity): LocalDate =
        Instant.ofEpochMilli(bill.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()

    /** 导入可接受的日期写法（含 Excel 编辑后常见的斜杠变体） */
    private val IN_DATETIME_PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm"
    )
    private val IN_DATE_ONLY_PATTERNS = listOf("yyyy-MM-dd", "yyyy/MM/dd")

    private val BILL_HEADERS = listOf("日期时间", "驾驶员", "车牌号", "内容", "金额", PHOTO_HEADER)

    data class ParsedBill(
        val amount: Double,
        val title: String,
        val driver: String = "",
        val plate: String = "",
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
     * - 固定列宽 日期时间20 / 标题40 / 金额20（货币 ¥ 两位小数）；
     * - 图片列宽 = 该列照片的显示宽（一张图的宽度），多张横向排开互不相隔过远；
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
            // 日期列只到日；同一天内按添加顺序（id 升序），天与天之间由早到晚
            val sorted = list.sortedWith(
                compareBy<BillEntity> { dayOf(it) }.thenBy { it.id }
            )
            val rows = listOf(BILL_HEADERS) + sorted.map { b ->
                val n = photosByBill[b.id].orEmpty().size
                listOf<Any?>(
                    formatTime(b.timestamp), b.driver, b.plate, b.title, b.amount,
                    if (n > 0) "${n}张" else null
                )
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
            // 列宽：固定三列 + 图片列取该列所有照片显示宽的最大值；数据行高统一 65 磅
            val colWidths = LinkedHashMap(FIXED_COL_WIDTHS)
            sorted.forEach { b ->
                photosByBill[b.id].orEmpty().forEachIndexed { k, p ->
                    val col = PHOTO_COL + k
                    colWidths[col] = maxOf(colWidths[col] ?: 0.0, colWidthChars(displayWidthPx(p)))
                }
            }
            val rowHeights = HashMap<Int, Double>()
            for (i in sorted.indices) rowHeights[i + 1] = DATA_ROW_HEIGHT_PT
            return MiniSheet(
                name = name,
                rows = rows,
                images = images,
                colWidths = colWidths,
                rowHeights = rowHeights,
                headerFill = true,
                colFormats = mapOf(4 to MONEY_FORMAT),
                total = MiniTotal(
                    label = "总金额：",
                    labelCol = 3,
                    valueCol = 4,
                    // 十进制累加避免二进制浮点尾巴（25.6+30.38 应为 55.98 而非 55.9800…04）
                    value = sorted.fold(java.math.BigDecimal.ZERO) { acc, b ->
                        acc + java.math.BigDecimal.valueOf(b.amount)
                    }.toDouble(),
                    formatCode = MONEY_FORMAT
                )
            )
        }

        return MiniXlsx.write(listOf(sheetData(SHEET_MAIN, bills)))
    }

    /**
     * 导出到缓存目录（cache/exports/，每次导出清空旧文件），返回生成的
     * xlsx 文件供系统分享面板发送（微信/文件管理器等）。
     */
    suspend fun exportToCache(
        context: Context,
        fileName: String,
        bills: List<BillEntity>,
        photosByBill: Map<Long, List<ExportPhoto>>
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, fileName)
            file.writeBytes(buildWorkbook(bills, photosByBill))
            file
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
        // 新格式唯一表「维修报销账单」；旧版兼容读「支出账单」。
        // 「收入账单」表（收入功能已移除）不解析、直接忽略。
        val sheet = sheets.firstOrNull { it.name == SHEET_MAIN }
            ?: sheets.firstOrNull { it.name == SHEET_EXPENSE }
            ?: throw IllegalArgumentException("未找到「$SHEET_MAIN」或「$SHEET_EXPENSE」工作表")

        val bills = ArrayList<ParsedBill>()
        var badRows = 0
        // 必需列：日期时间、金额。「内容」列兼容旧「标题」列名；
        // 驾驶员/车牌号为可选列（旧格式没有则导入为空）。
        val col = headerMap(sheet.rows.firstOrNull(), BILL_HEADERS, listOf("日期时间", "金额"), sheet.name)
        val titleCol = col["内容"] ?: col["标题"]
            ?: throw IllegalArgumentException("工作表「${sheet.name}」缺少列：内容")
        val driverCol = col["驾驶员"]
        val plateCol = col["车牌号"]
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
                    title = row.getOrNull(titleCol)?.toString()?.trim() ?: "",
                    driver = driverCol?.let { row.getOrNull(it)?.toString()?.trim() } ?: "",
                    plate = plateCol?.let { row.getOrNull(it)?.toString()?.trim() } ?: "",
                    timestamp = timestamp,
                    sheet = sheet.name,
                    row = rowIdx,
                    photos = parsePhotoRefs(str(row, col, PHOTO_HEADER))
                )
            } catch (e: Exception) {
                badRows++
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
            val key = fingerprint(bill.timestamp, bill.amount, bill.title, isIncome = false)
            if (key in existingKeys) {
                skipped++
                return@forEach
            }
            existingKeys.add(key)
            val billId = billDao.insert(
                BillEntity(
                    amount = bill.amount, title = bill.title,
                    driver = bill.driver, plate = bill.plate,
                    isIncome = false, timestamp = bill.timestamp
                )
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
