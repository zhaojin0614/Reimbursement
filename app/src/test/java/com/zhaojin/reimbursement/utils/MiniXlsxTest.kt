package com.zhaojin.reimbursement.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayOutputStream

class MiniXlsxTest {

    @Test
    fun `写读回环 - 文本数字转义与稀疏单元格`() {
        val sheet = MiniSheet(
            name = "测试表",
            rows = listOf(
                listOf("日期时间", "标题", "金额", "备注"),
                listOf("2026-08-30 22:31:05", "京东支付", 30.38, "含,逗号与\"引号\"&<标签>"),
                listOf<Any?>(null, null, null, "只有最后一列"),
                listOf("8500", "纯文本数字看做字符串", 8500.0, null)
            )
        )
        val bytes = MiniXlsx.write(listOf(sheet, MiniSheet("第二表", listOf(listOf<Any?>("A", 1)))))
        val sheets = MiniXlsx.read(bytes)

        assertEquals(2, sheets.size)
        assertEquals("测试表", sheets[0].name)
        assertEquals("第二表", sheets[1].name)

        val rows = sheets[0].rows
        assertEquals("日期时间", rows[0][0])
        assertEquals("标题", rows[0][1])
        assertEquals("30.38", rows[1][2])
        assertEquals("含,逗号与\"引号\"&<标签>", rows[1][3])
        assertNull(rows[2][0])
        assertNull(rows[2][2])
        assertEquals("只有最后一列", rows[2][3])
        assertEquals("8500", rows[3][2])
    }

    @Test
    fun `读 - 兼容Excel重存结构 sharedStrings与富文本`() {
        // 手工构造一份「Excel 风格」的 xlsx：字符串集中在 sharedStrings，
        // 单元格用 t="s" 引用，并包含富文本 <r><t> 合并片段
        val sharedStrings = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="6">
<si><t>日期时间</t></si>
<si><t>标题</t></si>
<si><r><t>富文本</t></r><r><t>拼接</t></r></si>
</sst>"""
        val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
<sheetData>
<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
<row r="2"><c r="A2" t="s"><v>2</v></c><c r="B2"><v>42.5</v></c></row>
</sheetData></worksheet>"""
        val bytes = buildZip(
            mapOf(
                "[Content_Types].xml" to "<Types/>",
                "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="支出账单" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
                "xl/sharedStrings.xml" to sharedStrings,
                "xl/worksheets/sheet1.xml" to sheet
            )
        )

        val sheets = MiniXlsx.read(bytes)
        assertEquals(1, sheets.size)
        assertEquals("支出账单", sheets[0].name)
        val rows = sheets[0].rows
        assertEquals("日期时间", rows[0][0])
        assertEquals("标题", rows[0][1])
        assertEquals("富文本拼接", rows[1][0])
        assertEquals("42.5", rows[1][1])
    }

    @Test
    fun `读 - 缺少workbook时抛出中文异常`() {
        val bytes = buildZip(mapOf("other.txt" to "hi"))
        val ex = assertThrows(IllegalArgumentException::class.java) { MiniXlsx.read(bytes) }
        assert(ex.message!!.contains("workbook.xml"))
    }

    @Test
    fun `列号与列名互转`() {
        assertEquals("A", MiniXlsx.colLetters(0))
        assertEquals("Z", MiniXlsx.colLetters(25))
        assertEquals("AA", MiniXlsx.colLetters(26))
        assertEquals(2, MiniXlsx.colIndexFromRef("C5"))
        assertEquals(26, MiniXlsx.colIndexFromRef("AA1"))
        // 关系 Target 路径定位：相对 / 绝对 / 已带 xl 前缀
        assertEquals("xl/media/a.jpg", MiniXlsx.resolveRelPath("xl/drawings", "../media/a.jpg"))
        assertEquals("xl/media/a.jpg", MiniXlsx.resolveRelPath("xl/drawings", "/xl/media/a.jpg"))
        assertEquals("xl/media/a.jpg", MiniXlsx.resolveRelPath("xl/worksheets", "../drawings/../media/a.jpg"))
    }

    @Test
    fun `图片内嵌 - 写读回环`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47) + byteArrayOf(1, 2, 3)
        val jpg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()) + byteArrayOf(9, 9)
        val sheet = MiniSheet(
            name = "带图",
            rows = listOf(listOf<Any?>("头", null, null, null, null)),
            images = listOf(MiniImage(row = 1, col = 3, data = png), MiniImage(row = 1, col = 4, data = jpg))
        )
        val wb = MiniXlsx.readWithImages(
            MiniXlsx.write(listOf(sheet, MiniSheet("空", listOf(listOf<Any?>("x")))))
        )
        assertEquals(2, wb.sheets.size)
        val byRow = wb.images["带图"].orEmpty()
        assertEquals(1, byRow.size)
        val row1 = byRow[1].orEmpty()
        assertEquals(2, row1.size)
        assertArrayEquals(png, row1[0])
        assertArrayEquals(jpg, row1[1])
        assertTrue(wb.images["空"] == null)
    }

    @Test
    fun `版式写出 - 列宽行高与表头样式`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val sheet = MiniSheet(
            name = "版式",
            rows = listOf(listOf<Any?>("头", null, null), listOf<Any?>("2026-01-01", "x", 1.0)),
            images = listOf(MiniImage(row = 1, col = 3, data = png, widthPx = 120, heightPx = 80)),
            colWidths = mapOf(0 to 20.0, 3 to 16.43),
            rowHeights = mapOf(1 to 60.0),
            headerFill = true
        )
        val bytes = MiniXlsx.write(listOf(sheet))
        val entry: (String) -> String = { name ->
            java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes)).use { zip ->
                var e = zip.nextEntry
                while (e != null) {
                    if (e.name == name) return@use zip.readBytes().toString(Charsets.UTF_8)
                    zip.closeEntry()
                    e = zip.nextEntry
                }
                throw IllegalArgumentException("缺少 $name")
            }
        }
        val sheetXml = entry("xl/worksheets/sheet1.xml")
        assertTrue(sheetXml.contains("""<col min="1" max="1" width="20" customWidth="1"/>"""))
        assertTrue(sheetXml.contains("""<col min="4" max="4" width="16.43" customWidth="1"/>"""))
        assertTrue(sheetXml.contains("""<row r="2" ht="60" customHeight="1">"""))
        assertTrue(sheetXml.contains("""<c r="A1" s="1" t="inlineStr">"""))
        assertTrue(!sheetXml.contains("""<c r="A2" s="1"""))
        assertTrue(entry("xl/styles.xml").contains("FFF2F2F2"))
        // 图片显示尺寸按像素换算 EMU：120x80px
        val drawing = entry("xl/drawings/drawing1.xml")
        assertTrue(drawing.contains("""cx="${120 * 9525}" cy="${80 * 9525}""""))
        // 带版式的表读回仍正常（版式仅影响写出，不影响解析）
        val wb = MiniXlsx.readWithImages(bytes)
        assertEquals("头", wb.sheets[0].rows[0][0])
        assertEquals(1, wb.images["版式"]!![1]!!.size)
    }

    @Test
    fun `图片内嵌 - 兼容Excel重存结构 twoCellAnchor与绝对路径`() {
        val media = "fake-jpeg-bytes"
        val sheet = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>x</t></is></c></row></sheetData>
<drawing r:id="rId7"/></worksheet>"""
        val drawing = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
<xdr:twoCellAnchor><xdr:from><xdr:col>3</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>2</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>
<xdr:to><xdr:col>4</xdr:col><xdr:colOff>0</xdr:colOff><xdr:row>3</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:to>
<xdr:pic><xdr:nvPicPr><xdr:cNvPr id="1" name="1"/><xdr:cNvPicPr/></xdr:nvPicPr>
<xdr:blipFill><a:blip r:embed="rId9"/><a:stretch/></xdr:blipFill><xdr:spPr/></xdr:pic>
<xdr:clientData/></xdr:twoCellAnchor></xdr:wsDr>"""
        val bytes = buildZip(
            mapOf(
                "[Content_Types].xml" to "<Types/>",
                "xl/workbook.xml" to """<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="支出账单" sheetId="1" r:id="rId1"/></sheets></workbook>""",
                "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
                "xl/worksheets/sheet1.xml" to sheet,
                "xl/worksheets/_rels/sheet1.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId7" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="/xl/drawings/drawing1.xml"/></Relationships>""",
                "xl/drawings/drawing1.xml" to drawing,
                "xl/drawings/_rels/drawing1.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId9" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="../media/image1.jpeg"/></Relationships>""",
                "xl/media/image1.jpeg" to media
            )
        )

        val wb = MiniXlsx.readWithImages(bytes)
        val byRow = wb.images["支出账单"].orEmpty()
        assertEquals(1, byRow[2].orEmpty().size)
        assertEquals(media, String(byRow[2]!![0], Charsets.UTF_8))
    }

    @Test
    fun `数字格式化 - 去尾零且无科学计数法`() {
        assertEquals("30.38", MiniXlsx.numberText(30.38))
        assertEquals("8500", MiniXlsx.numberText(8500.0))
        assertEquals("0.000001", MiniXlsx.numberText(0.000001))
    }

    private fun buildZip(entries: Map<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
