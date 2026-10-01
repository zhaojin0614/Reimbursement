package com.zhaojin.reimbursement.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class BillBackupManagerTest {

    private fun ts(local: LocalDateTime): Long =
        local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun sampleBills(): List<BillEntity> = listOf(
        BillEntity(
            id = 1, amount = 30.38, title = "京东支付",
            isIncome = false,
            timestamp = ts(LocalDateTime.of(2026, 8, 30, 22, 31, 5))
        ),
        BillEntity(
            id = 2, amount = 8500.0, title = "8月工资",
            isIncome = true,
            timestamp = ts(LocalDateTime.of(2026, 8, 10, 9, 0, 0))
        ),
        BillEntity(
            id = 3, amount = 25.6, title = "美团外卖",
            isIncome = false,
            timestamp = ts(LocalDateTime.of(2026, 8, 29, 18, 40, 0))
        )
    )

    @Test
    fun `导出再解析 - 收支分表完整往返`() {
        val bytes = BillBackupManager.buildWorkbook(sampleBills())
        val parsed = BillBackupManager.parseWorkbook(bytes)

        assertEquals(0, parsed.badRows)
        assertEquals(3, parsed.bills.size)

        val expense = parsed.bills.filter { !it.isIncome }
        val income = parsed.bills.filter { it.isIncome }
        assertEquals(2, expense.size)
        assertEquals(1, income.size)

        val jd = expense.first { it.title == "京东支付" }
        assertEquals(30.38, jd.amount, 1e-9)
        assertEquals(
            ts(LocalDateTime.of(2026, 8, 30, 22, 31, 5)), jd.timestamp
        )

        val salary = income.single()
        assertEquals(8500.0, salary.amount, 1e-9)
    }

    @Test
    fun `解析 - 乱序表头与多余列不受影响（兼容捕账与旧版备份）`() {
        val bytes = BillBackupManager.buildWorkbook(sampleBills())
        val sheets = com.zhaojin.reimbursement.utils.MiniXlsx.read(bytes)
        // 模拟「捕账」/旧版导出的备份：含 分类/平台/来源应用 等多余列，
        // 验证按列名映射、多余列自动忽略
        val extraHeaders: List<Any?> = listOf("日期时间", "分类", "标题", "金额", "平台", "来源应用", "次要来源", "来源包名", "次要包名")
        val expense = sheets.first { it.name == BillBackupManager.SHEET_EXPENSE }
        val permutedRows = expense.rows.drop(1).map { row ->
            listOf<Any?>(
                row[0], "餐饮美食", row[1], row[2],
                "微信钱包", "京东", "", "com.jingdong", ""
            )
        }
        val reBytes = com.zhaojin.reimbursement.utils.MiniXlsx.write(
            listOf(
                com.zhaojin.reimbursement.utils.MiniSheet(BillBackupManager.SHEET_EXPENSE, listOf(extraHeaders) + permutedRows),
                sheets.first { it.name == BillBackupManager.SHEET_INCOME }
            )
        )
        val parsed = BillBackupManager.parseWorkbook(reBytes)
        val jd = parsed.bills.first { it.title == "京东支付" }
        assertEquals(30.38, jd.amount, 1e-9)
    }

    @Test
    fun `解析 - 坏行计入失败但不影响好行`() {
        val bytes = BillBackupManager.buildWorkbook(sampleBills())
        // 直接构造坏行：日期不可解析 / 金额非法
        val sheets = com.zhaojin.reimbursement.utils.MiniXlsx.read(bytes)
        val expense = sheets.first { it.name == BillBackupManager.SHEET_EXPENSE }
        val badRows = expense.rows + listOf(
            listOf<Any?>("不是日期", "坏行一", 10.0),
            listOf<Any?>("2026-08-29 18:40:00", "坏行二", 0.0)
        )
        val reBytes = com.zhaojin.reimbursement.utils.MiniXlsx.write(
            listOf(
                com.zhaojin.reimbursement.utils.MiniSheet(BillBackupManager.SHEET_EXPENSE, badRows),
                sheets.first { it.name == BillBackupManager.SHEET_INCOME }
            )
        )
        val parsed = BillBackupManager.parseWorkbook(reBytes)
        assertEquals(2, parsed.badRows)
        assertEquals(3, parsed.bills.size)
    }

    @Test
    fun `解析 - 缺少账单工作表时抛出`() {
        val bytes = com.zhaojin.reimbursement.utils.MiniXlsx.write(
            listOf(com.zhaojin.reimbursement.utils.MiniSheet("其他表", listOf(listOf<Any?>("a"))))
        )
        assertThrows(IllegalArgumentException::class.java) { BillBackupManager.parseWorkbook(bytes) }
    }

    @Test
    fun `解析 - 缺少必需列时抛出`() {
        val bytes = com.zhaojin.reimbursement.utils.MiniXlsx.write(
            listOf(
                // 缺「金额」列
                com.zhaojin.reimbursement.utils.MiniSheet(
                    BillBackupManager.SHEET_EXPENSE,
                    listOf(listOf<Any?>("日期时间", "标题"))
                )
            )
        )
        val ex = assertThrows(IllegalArgumentException::class.java) { BillBackupManager.parseWorkbook(bytes) }
        assertTrue(ex.message!!.contains("金额"))
    }
}
