package com.zhaojin.reimbursement.data

import android.content.Context
import android.net.Uri
import com.zhaojin.reimbursement.utils.MiniSheet
import com.zhaojin.reimbursement.utils.MiniXlsx
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 账单数据备份管理器：单一 xlsx 工作簿承载全部记账数据。
 *
 * Sheet 结构：
 * - 「支出账单」「收入账单」：日期时间/分类/标题/金额
 *
 * 表头按列名映射（顺序无关、多余列忽略），向前兼容未来加列；
 * 也能直接导入「捕账」导出的备份（多余的 平台/来源应用 等列自动忽略）。
 */
object BillBackupManager {

    const val SHEET_EXPENSE = "支出账单"
    const val SHEET_INCOME = "收入账单"

    private val OUT_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /** 导入可接受的日期写法（含 Excel 编辑后常见的斜杠变体） */
    private val IN_DATETIME_PATTERNS = listOf(
        "yyyy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss",
        "yyyy-MM-dd HH:mm", "yyyy/MM/dd HH:mm"
    )
    private val IN_DATE_ONLY_PATTERNS = listOf("yyyy-MM-dd", "yyyy/MM/dd")

    private val BILL_HEADERS = listOf("日期时间", "分类", "标题", "金额")

    data class ParsedBill(
        val amount: Double,
        val title: String,
        val category: String,
        val isIncome: Boolean,
        val timestamp: Long
    )

    data class ParsedWorkbook(
        val bills: List<ParsedBill>,
        val badRows: Int
    )

    data class BackupWriteResult(
        val total: Int,
        val inserted: Int,
        val skipped: Int,
        val failed: Int
    ) {
        fun summary(): String = buildString {
            append("新增 $inserted 笔、跳过 $skipped 笔、失败 $failed 笔")
        }
    }

    // ------------------------------------------------------------------
    // 导出
    // ------------------------------------------------------------------

    /** 构建工作簿字节（纯函数，便于单测） */
    fun buildWorkbook(bills: List<BillEntity>): ByteArray {
        fun formatTime(ts: Long) =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()).format(OUT_FMT)

        fun billRows(list: List<BillEntity>): List<List<Any?>> =
            listOf(BILL_HEADERS) + list.sortedBy { it.timestamp }.map { b ->
                listOf<Any?>(
                    formatTime(b.timestamp), b.category, b.title, b.amount
                )
            }

        return MiniXlsx.write(
            listOf(
                MiniSheet(SHEET_EXPENSE, billRows(bills.filterNot { it.isIncome })),
                MiniSheet(SHEET_INCOME, billRows(bills.filter { it.isIncome }))
            )
        )
    }

    suspend fun exportToUri(
        context: Context,
        uri: Uri,
        bills: List<BillEntity>
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = buildWorkbook(bills)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: throw IllegalStateException("无法写入所选位置")
            bills.size
        }
    }

    // ------------------------------------------------------------------
    // 导入
    // ------------------------------------------------------------------

    /** 解析工作簿（纯函数，便于单测）；结构缺失抛 IllegalArgumentException。 */
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
                        category = str(row, col, "分类") ?: "未分类",
                        isIncome = isIncome,
                        timestamp = timestamp
                    )
                } catch (e: Exception) {
                    badRows++
                }
            }
        }
        return ParsedWorkbook(bills, badRows)
    }

    /**
     * 将解析出的账单写入库。overwrite=false 合并（按 时间+金额+标题+类型
     * 指纹去重，跳过已存在的账单）；true 恢复覆盖（清空账单后按文件重建）。
     */
    suspend fun importBills(
        dao: BillDao,
        parsed: ParsedWorkbook,
        overwrite: Boolean
    ): BackupWriteResult {
        if (overwrite) dao.deleteAll()
        val existingKeys = if (overwrite) HashSet<String>() else HashSet(
            dao.getAllBillsOnce()
                .map { fingerprint(it.timestamp, it.amount, it.title, it.isIncome) }
        )
        val toInsert = ArrayList<BillEntity>()
        var skipped = 0
        parsed.bills.forEach { bill ->
            val key = fingerprint(bill.timestamp, bill.amount, bill.title, bill.isIncome)
            if (key in existingKeys) {
                skipped++
            } else {
                existingKeys.add(key)
                toInsert += BillEntity(
                    amount = bill.amount,
                    title = bill.title,
                    category = bill.category,
                    isIncome = bill.isIncome,
                    timestamp = bill.timestamp
                )
            }
        }
        dao.insertAll(toInsert)
        return BackupWriteResult(
            total = parsed.bills.size,
            inserted = toInsert.size,
            skipped = skipped,
            failed = parsed.badRows
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
