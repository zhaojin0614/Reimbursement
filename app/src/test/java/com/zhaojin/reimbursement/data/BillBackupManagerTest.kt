package com.zhaojin.reimbursement.data

import org.junit.Assert.assertArrayEquals
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

    /** 账单1 两张图、账单3 一张图、账单2 无图 */
    private fun samplePhotos(): Map<Long, List<String>> = mapOf(
        1L to listOf("aaa1.jpg", "bbb2.jpg"),
        3L to listOf("ccc3.jpg")
    )

    private fun fakeImage(name: String): ByteArray = "img-bytes-of-$name".toByteArray()

    @Test
    fun `zip备份往返 - 表格图片列与图片字节完整还原`() {
        val zipBytes = BillBackupManager.buildZip(sampleBills(), samplePhotos(), ::fakeImage)
        val backup = BillBackupManager.parseBackup(zipBytes)

        assertEquals(0, backup.workbook.badRows)
        assertEquals(3, backup.workbook.bills.size)
        // 图片字节按引用名进包
        assertEquals(setOf("aaa1.jpg", "bbb2.jpg", "ccc3.jpg"), backup.images.keys)
        assertArrayEquals(fakeImage("aaa1.jpg"), backup.images["aaa1.jpg"])

        val jd = backup.workbook.bills.first { it.title == "京东支付" }
        assertEquals(30.38, jd.amount, 1e-9)
        assertEquals(listOf("aaa1.jpg", "bbb2.jpg"), jd.photos)
        assertTrue(backup.workbook.bills.first { it.title == "8月工资" }.photos.isEmpty())
        assertEquals(listOf("ccc3.jpg"), backup.workbook.bills.first { it.title == "美团外卖" }.photos)
    }

    @Test
    fun `解析 - 表格头乱序与捕账多余列不受影响`() {
        // 基于「无图工作簿」构造捕账式表格：列更多（分类/平台/来源应用等）且顺序不同
        val sheets0 = com.zhaojin.reimbursement.utils.MiniXlsx.read(
            BillBackupManager.buildWorkbook(sampleBills(), emptyMap())
        )
        val expense = sheets0.first { it.name == BillBackupManager.SHEET_EXPENSE }
        val legacyHeaders: List<Any?> = listOf("日期时间", "分类", "标题", "金额", "平台", "来源应用", "次要来源", "来源包名", "次要包名")
        val legacyRows = expense.rows.drop(1).map { row ->
            listOf<Any?>(row[0], "餐饮美食", row[1], row[2], "微信钱包", "京东", "", "com.jingdong", "")
        }
        val reBytes = com.zhaojin.reimbursement.utils.MiniXlsx.write(
            listOf(
                com.zhaojin.reimbursement.utils.MiniSheet(BillBackupManager.SHEET_EXPENSE, listOf(legacyHeaders) + legacyRows),
                sheets0.first { it.name == BillBackupManager.SHEET_INCOME }
            )
        )
        val parsed = BillBackupManager.parseBackup(reBytes)
        val jd = parsed.workbook.bills.first { it.title == "京东支付" }
        assertEquals(30.38, jd.amount, 1e-9)
        assertTrue("捕账格式无图片列 → 无图", jd.photos.isEmpty())
        assertTrue(parsed.images.isEmpty())
    }

    @Test
    fun `解析 - 纯xlsx（旧版备份）识别为无图片`() {
        val xlsxBytes = BillBackupManager.buildWorkbook(sampleBills(), samplePhotos())
        val backup = BillBackupManager.parseBackup(xlsxBytes)
        assertTrue(backup.images.isEmpty())
        // 表格里有图片列引用（buildWorkbook 生成），但纯 xlsx 导入时图片字节缺失
        assertEquals(3, backup.workbook.bills.size)
    }

    @Test
    fun `解析 - 坏行计入失败但不影响好行`() {
        val bytes = BillBackupManager.buildWorkbook(sampleBills(), emptyMap())
        val sheets = com.zhaojin.reimbursement.utils.MiniXlsx.read(bytes)
        val expense = sheets.first { it.name == BillBackupManager.SHEET_EXPENSE }
        val badRows = expense.rows + listOf(
            listOf<Any?>("不是日期", "坏行一", 10.0, ""),
            listOf<Any?>("2026-08-29 18:40:00", "坏行二", 0.0, "")
        )
        val reBytes = com.zhaojin.reimbursement.utils.MiniXlsx.write(
            listOf(
                com.zhaojin.reimbursement.utils.MiniSheet(BillBackupManager.SHEET_EXPENSE, badRows),
                sheets.first { it.name == BillBackupManager.SHEET_INCOME }
            )
        )
        val parsed = BillBackupManager.parseBackup(reBytes)
        assertEquals(2, parsed.workbook.badRows)
        assertEquals(3, parsed.workbook.bills.size)
    }

    @Test
    fun `解析 - 缺少账单工作表时抛出`() {
        val bytes = com.zhaojin.reimbursement.utils.MiniXlsx.write(
            listOf(com.zhaojin.reimbursement.utils.MiniSheet("其他表", listOf(listOf<Any?>("a"))))
        )
        assertThrows(IllegalArgumentException::class.java) { BillBackupManager.parseBackup(bytes) }
    }

    @Test
    fun `解析 - 缺少必需列时抛出`() {
        val bytes = com.zhaojin.reimbursement.utils.MiniXlsx.write(
            listOf(
                com.zhaojin.reimbursement.utils.MiniSheet(
                    BillBackupManager.SHEET_EXPENSE,
                    listOf(listOf<Any?>("日期时间", "标题"))
                )
            )
        )
        val ex = assertThrows(IllegalArgumentException::class.java) { BillBackupManager.parseBackup(bytes) }
        assertTrue(ex.message!!.contains("金额"))
    }

    @Test
    fun `解析 - zip包缺账单表格时抛出`() {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("images/x.jpg"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) { BillBackupManager.parseBackup(out.toByteArray()) }
    }
}
