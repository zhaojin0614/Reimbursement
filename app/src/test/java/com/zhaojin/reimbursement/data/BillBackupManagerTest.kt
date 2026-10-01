package com.zhaojin.reimbursement.data

import com.zhaojin.reimbursement.utils.MiniSheet
import com.zhaojin.reimbursement.utils.MiniXlsx
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

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

    /** 带 PNG 魔数的假图片字节（解析层不解码，仅按字节透传对位） */
    private fun fakeImage(name: String): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + "-$name".toByteArray()

    private fun photo(name: String, w: Int, h: Int) = BillBackupManager.ExportPhoto(fakeImage(name), w, h)

    /** 账单1 两张图（方图+2:1横图）、账单3 一张 1:2 竖图、账单2 无图 */
    private fun samplePhotos(): Map<Long, List<BillBackupManager.ExportPhoto>> = mapOf(
        1L to listOf(photo("a1", 100, 100), photo("b2", 200, 100)),
        3L to listOf(photo("c3", 100, 200))
    )

    private fun unzipEntry(bytes: ByteArray, entry: String): String {
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (e.name == entry) return zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry()
                e = zip.nextEntry
            }
        }
        throw IllegalArgumentException("缺少 $entry")
    }

    @Test
    fun `xlsx导出往返 - 照片内嵌按行还原`() {
        val xlsxBytes = BillBackupManager.buildWorkbook(sampleBills(), samplePhotos())
        val backup = BillBackupManager.parseBackup(xlsxBytes)

        assertEquals(0, backup.workbook.badRows)
        assertEquals(3, backup.workbook.bills.size)

        // 支出表按时间排序：美团(8-29) 行1，京东(8-30) 行2；京东两张图横向排开
        val jd = backup.workbook.bills.first { it.title == "京东支付" }
        assertEquals(30.38, jd.amount, 1e-9)
        assertEquals(BillBackupManager.SHEET_EXPENSE, jd.sheet)
        val jdImages = backup.embedded[jd.sheet]?.get(jd.row).orEmpty()
        assertEquals(2, jdImages.size)
        assertArrayEquals(fakeImage("a1"), jdImages[0])
        assertArrayEquals(fakeImage("b2"), jdImages[1])

        val salary = backup.workbook.bills.first { it.title == "8月工资" }
        assertEquals(BillBackupManager.SHEET_INCOME, salary.sheet)
        assertTrue(backup.embedded[salary.sheet]?.get(salary.row).orEmpty().isEmpty())

        val mt = backup.workbook.bills.first { it.title == "美团外卖" }
        val mtImages = backup.embedded[mt.sheet]?.get(mt.row).orEmpty()
        assertEquals(1, mtImages.size)
        assertArrayEquals(fakeImage("c3"), mtImages[0])
    }

    @Test
    fun `解析 - 无图工作簿内嵌为空`() {
        val backup = BillBackupManager.parseBackup(
            BillBackupManager.buildWorkbook(sampleBills(), emptyMap())
        )
        assertTrue(backup.embedded.isEmpty())
        assertTrue(backup.images.isEmpty())
        assertEquals(3, backup.workbook.bills.size)
    }

    @Test
    fun `导出版式 - 列宽行高表头底色与合计行`() {
        val bytes = BillBackupManager.buildWorkbook(sampleBills(), samplePhotos())
        val sheet1 = unzipEntry(bytes, "xl/worksheets/sheet1.xml")

        // 固定列宽：日期时间 20 / 标题 40 / 金额 20（min/max 为 1 基）
        assertTrue(sheet1.contains("""<col min="1" max="1" width="20" customWidth="1"/>"""))
        assertTrue(sheet1.contains("""<col min="2" max="2" width="40" customWidth="1"/>"""))
        assertTrue(sheet1.contains("""<col min="3" max="3" width="20" customWidth="1"/>"""))
        // 图片列宽 = 一张图的显示宽（该列最大值）：
        // D 列 max(竖图40px, 方图80px)=80→10.71，E 列 横图160px→22.14
        assertTrue(sheet1.contains("""<col min="4" max="4" width="10.71" customWidth="1"/>"""))
        assertTrue(sheet1.contains("""<col min="5" max="5" width="22.14" customWidth="1"/>"""))
        assertTrue(!sheet1.contains("""width="200""""))
        // 数据行高统一 65 磅（表头占第 1 行，数据行 2、3）
        assertTrue(sheet1.contains("""<row r="2" ht="65" customHeight="1">"""))
        assertTrue(sheet1.contains("""<row r="3" ht="65" customHeight="1">"""))
        // 表头单元格套浅灰底样式 s="1"，数据单元格不带
        assertTrue(sheet1.contains("""<c r="A1" s="1" t="inlineStr">"""))
        assertTrue(!sheet1.contains("""<c r="A2" s="1"""))
        // 金额列为货币格式样式 s="2"（¥ 两位小数）
        assertTrue(sheet1.contains("""<c r="C2" s="2"><v>25.6</v></c>"""))
        // 合计行在最后一行数据之下：B 列「总金额：」s=3，C 列数值 s=4（25.6+30.38）
        assertTrue(sheet1.contains("""<row r="4">"""))
        assertTrue(sheet1.contains("""<c r="B4" s="3" t="inlineStr">"""))
        assertTrue(sheet1.contains("总金额："))
        assertTrue(sheet1.contains("""<c r="C4" s="4"><v>55.98</v></c>"""))
        // 样式表：货币 numFmt、红字、黄底
        val styles = unzipEntry(bytes, "xl/styles.xml")
        assertTrue(styles.contains("""numFmtId="164" formatCode="&quot;¥&quot;#,##0.00""""))
        assertTrue(styles.contains("FFFF0000"))
        assertTrue(styles.contains("FFFFFF00"))

        // 收入表：无照片，无图片列；数据行也 65；合计行 8500
        val sheet2 = unzipEntry(bytes, "xl/worksheets/sheet2.xml")
        assertTrue(sheet2.contains("""<col min="1" max="1" width="20" customWidth="1"/>"""))
        assertTrue(!sheet2.contains("""<col min="4""""))
        assertTrue(sheet2.contains("""<row r="2" ht="65" customHeight="1">"""))
        assertTrue(sheet2.contains("""<c r="B3" s="3" t="inlineStr">"""))
        assertTrue(sheet2.contains("""<c r="C3" s="4"><v>8500</v></c>"""))
    }

    @Test
    fun `解析 - 跳过合计行不误当账单或坏行`() {
        val backup = BillBackupManager.parseBackup(
            BillBackupManager.buildWorkbook(sampleBills(), samplePhotos())
        )
        assertEquals(3, backup.workbook.bills.size)
        assertEquals(0, backup.workbook.badRows)
        assertTrue(backup.workbook.bills.none { it.title.contains("总金额") })
    }

    @Test
    fun `导出排序与日期 - 只到日且同日按添加顺序`() {
        val sameDay = ts(LocalDateTime.of(2026, 8, 30, 8, 0, 0))
        // 同一天三笔，id 顺序 30 → 10 → 20；另一笔 8-29
        val bills = listOf(
            BillEntity(id = 30, amount = 1.0, title = "三号", isIncome = false, timestamp = sameDay + 6_000),
            BillEntity(id = 10, amount = 2.0, title = "一号", isIncome = false, timestamp = sameDay),
            BillEntity(id = 20, amount = 3.0, title = "二号", isIncome = false, timestamp = sameDay + 3_000),
            BillEntity(id = 40, amount = 4.0, title = "前一日", isIncome = false,
                timestamp = ts(LocalDateTime.of(2026, 8, 29, 23, 0, 0)))
        )
        val backup = BillBackupManager.parseBackup(BillBackupManager.buildWorkbook(bills, emptyMap()))
        val titles = backup.workbook.bills.map { it.title }
        // 天由早到晚；8-30 当天按 id（添加顺序）一号→二号→三号
        assertEquals(listOf("前一日", "一号", "二号", "三号"), titles)
        // 日期列只到日：解析回 8-30 12:00（日期缺省正午）
        backup.workbook.bills.filter { it.title != "前一日" }.forEach {
            assertEquals(
                LocalDateTime.of(2026, 8, 30, 12, 0, 0),
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it.timestamp), ZoneId.systemDefault())
            )
        }
    }

    @Test
    fun `导出版式 - 图片锚点按宽高比定尺寸`() {
        val bytes = BillBackupManager.buildWorkbook(sampleBills(), samplePhotos())
        val drawing = unzipEntry(bytes, "xl/drawings/drawing1.xml")
        // 竖图 100x200 → 40x80px；方图 100x100 → 80x80px；横图 200x100 → 160x80px
        assertTrue(drawing.contains("""cx="${40 * 9525}" cy="${80 * 9525}""""))
        assertTrue(drawing.contains("""cx="${80 * 9525}" cy="${80 * 9525}""""))
        assertTrue(drawing.contains("""cx="${160 * 9525}" cy="${80 * 9525}""""))
        // 锚点从格子原点起（0 偏移），图片精确贴合格子
        assertTrue(drawing.contains("<xdr:colOff>0</xdr:colOff><xdr:row>"))
    }

    @Test
    fun `旧版zip备份 - 图片列引用仍可还原`() {
        val rows = listOf(
            listOf<Any?>("日期时间", "标题", "金额", "图片"),
            listOf<Any?>("2026-08-29 18:40:00", "美团外卖", "25.6", "images/ccc3.jpg"),
            listOf<Any?>("2026-08-30 22:31:05", "京东支付", "30.38", "images/aaa1.jpg; images/bbb2.jpg")
        )
        val inner = MiniXlsx.write(
            listOf(MiniSheet(BillBackupManager.SHEET_EXPENSE, rows))
        )
        val zipBytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry(BillBackupManager.ZIP_XLSX_ENTRY))
                zip.write(inner)
                zip.closeEntry()
                listOf("aaa1.jpg", "bbb2.jpg", "ccc3.jpg").forEach { name ->
                    zip.putNextEntry(ZipEntry("${BillBackupManager.ZIP_IMAGES_DIR}$name"))
                    zip.write(fakeImage(name))
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        val backup = BillBackupManager.parseBackup(zipBytes)
        assertEquals(setOf("aaa1.jpg", "bbb2.jpg", "ccc3.jpg"), backup.images.keys)
        assertEquals(2, backup.workbook.bills.size)
        val jd = backup.workbook.bills.first { it.title == "京东支付" }
        assertEquals(listOf("aaa1.jpg", "bbb2.jpg"), jd.photos)
        assertEquals(30.38, jd.amount, 1e-9)
        assertTrue(backup.workbook.bills.first { it.title == "美团外卖" }.photos == listOf("ccc3.jpg"))
    }

    @Test
    fun `解析 - 捕账式表格头乱序与多余列不受影响`() {
        val sheets0 = MiniXlsx.read(BillBackupManager.buildWorkbook(sampleBills(), emptyMap()))
        val expense = sheets0.first { it.name == BillBackupManager.SHEET_EXPENSE }
        val legacyHeaders: List<Any?> =
            listOf("日期时间", "分类", "标题", "金额", "平台", "来源应用", "次要来源", "来源包名", "次要包名")
        val legacyRows = expense.rows.drop(1).map { row ->
            listOf<Any?>(row[0], "餐饮美食", row[1], row[2], "微信钱包", "京东", "", "com.jingdong", "")
        }
        val reBytes = MiniXlsx.write(
            listOf(
                MiniSheet(BillBackupManager.SHEET_EXPENSE, listOf(legacyHeaders) + legacyRows),
                sheets0.first { it.name == BillBackupManager.SHEET_INCOME }
            )
        )
        val parsed = BillBackupManager.parseBackup(reBytes)
        val jd = parsed.workbook.bills.first { it.title == "京东支付" }
        assertEquals(30.38, jd.amount, 1e-9)
        assertTrue("捕账格式无图片列 → 无图", jd.photos.isEmpty())
        assertTrue(parsed.images.isEmpty() && parsed.embedded.isEmpty())
    }

    @Test
    fun `解析 - 坏行计入失败但不影响好行`() {
        val bytes = BillBackupManager.buildWorkbook(sampleBills(), emptyMap())
        val sheets = MiniXlsx.read(bytes)
        val expense = sheets.first { it.name == BillBackupManager.SHEET_EXPENSE }
        val badRows = expense.rows + listOf(
            listOf<Any?>("不是日期", "坏行一", 10.0, ""),
            listOf<Any?>("2026-08-29 18:40:00", "坏行二", 0.0, "")
        )
        val reBytes = MiniXlsx.write(
            listOf(
                MiniSheet(BillBackupManager.SHEET_EXPENSE, badRows),
                sheets.first { it.name == BillBackupManager.SHEET_INCOME }
            )
        )
        val parsed = BillBackupManager.parseBackup(reBytes)
        assertEquals(2, parsed.workbook.badRows)
        assertEquals(3, parsed.workbook.bills.size)
    }

    @Test
    fun `解析 - 新格式图片计数列不误判为图片引用`() {
        // 图片列是「N张」计数文本（无路径分隔符），不得当成 zip 引用
        val backup = BillBackupManager.parseBackup(
            BillBackupManager.buildWorkbook(sampleBills(), samplePhotos())
        )
        backup.workbook.bills.forEach { bill ->
            assertTrue("「N张」文本不应产生图片引用：${bill.photos}", bill.photos.isEmpty())
        }
    }

    @Test
    fun `解析 - 缺少账单工作表时抛出`() {
        val bytes = MiniXlsx.write(
            listOf(MiniSheet("其他表", listOf(listOf<Any?>("a"))))
        )
        assertThrows(IllegalArgumentException::class.java) { BillBackupManager.parseBackup(bytes) }
    }

    @Test
    fun `解析 - 缺少必需列时抛出`() {
        val bytes = MiniXlsx.write(
            listOf(
                MiniSheet(
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
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("images/x.jpg"))
            zip.write(byteArrayOf(1))
            zip.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) { BillBackupManager.parseBackup(out.toByteArray()) }
    }
}
