package com.zhaojin.reimbursement.utils

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 单元格值约定（写方向）：
 * - String  → 文本单元格（inlineStr）
 * - Double / Long / Int → 数字单元格（可参与 Excel 求和/透视）
 * - null → 空单元格
 * 读方向统一返回文本（数字即其字面量），由调用方自行解析。
 */
typealias MiniRow = List<Any?>

data class MiniSheet(
    val name: String,
    val rows: List<MiniRow>,
    val images: List<MiniImage> = emptyList(),
    /** 列宽（Excel 字符单位，0 基列号 → 宽度），仅写出的列带 customWidth */
    val colWidths: Map<Int, Double> = emptyMap(),
    /** 行高（磅，0 基行号 → 高度），仅写出的行带 customHeight */
    val rowHeights: Map<Int, Double> = emptyMap(),
    /** 表头行（首行）填充浅灰背景 */
    val headerFill: Boolean = false
)

/**
 * 内嵌图片（OOXML drawing 层）：锚定在 (row, col) 单元格（0 基）原点，
 * 显示尺寸 [widthPx]×[heightPx]（96dpi 像素）。配合与图片等宽的列宽、
 * 等高的行高，图片即精确填满格子。data 须为 JPEG/PNG 字节。
 */
data class MiniImage(
    val row: Int,
    val col: Int,
    val data: ByteArray,
    val widthPx: Int = 80,
    val heightPx: Int = 80
)

/** [MiniXlsx.readWithImages] 的返回：工作表 + 各表内嵌图片（表名 → 物理行 → 字节列表） */
data class MiniWorkbook(
    val sheets: List<MiniSheet>,
    val images: Map<String, Map<Int, List<ByteArray>>>
)

/**
 * 轻量 xlsx（OOXML SpreadsheetML）读写器，无第三方依赖。
 *
 * 写出：内联字符串 + 数字单元格 + 最小 styles.xml + 可选内嵌图片
 * （xl/drawings + xl/media，Excel/WPS 打开直接可见），均无第三方依赖；
 * 读取：兼容 Excel/WPS 重存后的标准结构（sharedStrings、富文本 <r><t> 合并、
 * 单元格稀疏排布、命名空间前缀差异、oneCell/twoCell 锚点）。日期一律按文本
 * 处理，不做序列号转换。
 */
object MiniXlsx {

    private const val NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val NS_DOC_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val NS_XDR = "http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing"
    private const val NS_A = "http://schemas.openxmlformats.org/drawingml/2006/main"

    /** 96dpi 下 1 像素 = 9525 EMU（drawing 显示尺寸单位） */
    private const val EMU_PER_PX = 9525

    // ------------------------------------------------------------------
    // 写
    // ------------------------------------------------------------------

    fun write(sheets: List<MiniSheet>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putEntryAndWrite("[Content_Types].xml", contentTypesXml(sheets))
            zip.putEntryAndWrite("_rels/.rels", ROOT_RELS)
            zip.putEntryAndWrite("xl/workbook.xml", workbookXml(sheets))
            zip.putEntryAndWrite("xl/_rels/workbook.xml.rels", workbookRelsXml(sheets.size))
            zip.putEntryAndWrite("xl/styles.xml", STYLES_XML)
            var mediaSeq = 0 // 全局媒体编号 xl/media/image1..N
            sheets.forEachIndexed { i, sheet ->
                val hasImages = sheet.images.isNotEmpty()
                zip.putEntryAndWrite(
                    "xl/worksheets/sheet${i + 1}.xml",
                    sheetXml(sheet, if (hasImages) "rId1" else null)
                )
                if (!hasImages) return@forEachIndexed
                zip.putEntryAndWrite(
                    "xl/worksheets/_rels/sheet${i + 1}.xml.rels",
                    relsXml("""<Relationship Id="rId1" Type="$NS_DOC_REL/drawing" Target="../drawings/drawing${i + 1}.xml"/>""")
                )
                val anchors = StringBuilder()
                val rels = StringBuilder()
                sheet.images.forEachIndexed { k, img ->
                    mediaSeq++
                    val mediaName = "image$mediaSeq.${imageExtension(img.data)}"
                    anchors.append(picAnchor(img, "rId${k + 1}", k + 1))
                    rels.append("""<Relationship Id="rId${k + 1}" Type="$NS_DOC_REL/image" Target="../media/$mediaName"/>""")
                    zip.putMediaEntry("xl/media/$mediaName", img.data)
                }
                zip.putEntryAndWrite("xl/drawings/drawing${i + 1}.xml", drawingXml(anchors.toString()))
                zip.putEntryAndWrite(
                    "xl/drawings/_rels/drawing${i + 1}.xml.rels",
                    relsXml(rels.toString())
                )
            }
        }
        return out.toByteArray()
    }

    private fun ZipOutputStream.putEntryAndWrite(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun ZipOutputStream.putMediaEntry(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(data)
        closeEntry()
    }

    private fun contentTypesXml(sheets: List<MiniSheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Default Extension="jpeg" ContentType="image/jpeg"/>""")
        append("""<Default Extension="jpg" ContentType="image/jpeg"/>""")
        append("""<Default Extension="png" ContentType="image/png"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheets.size) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        sheets.forEachIndexed { i, sheet ->
            if (sheet.images.isNotEmpty()) {
                append("""<Override PartName="/xl/drawings/drawing${i + 1}.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
            }
        }
        append("</Types>")
    }

    private val ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="$NS_DOC_REL/officeDocument" Target="xl/workbook.xml"/></Relationships>"""

    private fun relsXml(inner: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">$inner</Relationships>"""

    private fun workbookXml(sheets: List<MiniSheet>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="$NS_MAIN" xmlns:r="$NS_DOC_REL"><sheets>""")
        sheets.forEachIndexed { i, sheet ->
            val name = xmlEscape(sheet.name).take(31) // Excel 表名上限 31 字符
            append("""<sheet name="$name" sheetId="${i + 1}" r:id="rId${i + 1}"/>""")
        }
        append("</sheets></workbook>")
    }

    private fun workbookRelsXml(count: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..count) {
            append("""<Relationship Id="rId$i" Type="$NS_DOC_REL/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${count + 1}" Type="$NS_DOC_REL/styles" Target="styles.xml"/>""")
        append("</Relationships>")
    }

    private val STYLES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="$NS_MAIN"><fonts count="1"><font><sz val="11"/><name val="Calibri"/></font></fonts><fills count="3"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill><fill><patternFill patternType="solid"><fgColor rgb="FFF2F2F2"/><bgColor indexed="64"/></patternFill></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs><cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="0" fillId="2" borderId="0" xfId="0" applyFill="1"/></cellXfs></styleSheet>"""

    private fun sheetXml(sheet: MiniSheet, drawingRid: String? = null): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        // CT_Worksheet 元素顺序：cols 在 sheetData 之前，drawing 在其后
        append("""<worksheet xmlns="$NS_MAIN" xmlns:r="$NS_DOC_REL">""")
        if (sheet.colWidths.isNotEmpty()) {
            append("<cols>")
            sheet.colWidths.toSortedMap().forEach { (col, width) ->
                append("""<col min="${col + 1}" max="${col + 1}" width="${numberText(width)}" customWidth="1"/>""")
            }
            append("</cols>")
        }
        append("<sheetData>")
        sheet.rows.forEachIndexed { r, row ->
            val height = sheet.rowHeights[r]
            if (height != null) {
                append("""<row r="${r + 1}" ht="${numberText(height)}" customHeight="1">""")
            } else {
                append("""<row r="${r + 1}">""")
            }
            row.forEachIndexed { c, cell ->
                val ref = "${colLetters(c)}${r + 1}"
                // 表头行非空单元格套浅灰底样式（cellXfs 第 2 项）
                val style = if (r == 0 && sheet.headerFill && cell != null) """ s="1"""" else ""
                when (cell) {
                    null -> {}
                    is Number -> append("""<c r="$ref"$style><v>${numberText(cell.toDouble())}</v></c>""")
                    else -> append("""<c r="$ref"$style t="inlineStr"><is><t xml:space="preserve">${xmlEscape(cell.toString())}</t></is></c>""")
                }
            }
            append("</row>")
        }
        append("</sheetData>")
        if (drawingRid != null) append("""<drawing r:id="$drawingRid"/>""")
        append("</worksheet>")
    }

    private fun drawingXml(anchors: String): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<xdr:wsDr xmlns:xdr="$NS_XDR" xmlns:a="$NS_A">$anchors</xdr:wsDr>"""

    /**
     * 单图锚点（oneCellAnchor）：从单元格原点起、按 [MiniImage] 显示尺寸铺开，
     * 不留边距——配合等高行与等宽列即视觉上"图片在格子里"。
     */
    private fun picAnchor(img: MiniImage, rid: String, picIndex: Int): String {
        val cx = img.widthPx * EMU_PER_PX
        val cy = img.heightPx * EMU_PER_PX
        return buildString {
            append("<xdr:oneCellAnchor>")
            append("<xdr:from><xdr:col>${img.col}</xdr:col><xdr:colOff>0</xdr:colOff>")
            append("<xdr:row>${img.row}</xdr:row><xdr:rowOff>0</xdr:rowOff></xdr:from>")
            append("""<xdr:ext cx="$cx" cy="$cy"/>""")
            append("""<xdr:pic><xdr:nvPicPr><xdr:cNvPr id="$picIndex" name="图片$picIndex"/>""")
            append("""<xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr>""")
            append("""<xdr:blipFill><a:blip xmlns:r="$NS_DOC_REL" r:embed="$rid"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>""")
            append("""<xdr:spPr><a:xfrm><a:off x="0" y="0"/><a:ext cx="$cx" cy="$cy"/></a:xfrm>""")
            append("""<a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr>""")
            append("</xdr:pic><xdr:clientData/></xdr:oneCellAnchor>")
        }
    }

    /** 图片扩展名按字节魔数判定；调用方须保证已是 JPEG/PNG（其他格式先转码） */
    private fun imageExtension(data: ByteArray): String = when {
        data.size >= 3 && data[0] == 0xFF.toByte() && data[1] == 0xD8.toByte() && data[2] == 0xFF.toByte() -> "jpeg"
        else -> "png"
    }

    /** 数字去尾零且无科学计数法：30.38→"30.38"，8500.0→"8500"（valueOf 走最短字符串表示，避免二进制精度尾巴） */
    internal fun numberText(value: Double): String =
        java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()

    internal fun xmlEscape(s: String): String {
        val sb = StringBuilder(s.length)
        s.forEach { ch ->
            when {
                ch == '&' -> sb.append("&amp;")
                ch == '<' -> sb.append("&lt;")
                ch == '>' -> sb.append("&gt;")
                ch == '"' -> sb.append("&quot;")
                ch == '\'' -> sb.append("&apos;")
                ch == '\t' || ch == '\n' || ch == '\r' -> sb.append(ch)
                ch.code < 0x20 -> {} // 丢弃 XML 非法控制字符
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    internal fun colLetters(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // 读
    // ------------------------------------------------------------------

    /** 解析工作簿；结构缺失时抛 [IllegalArgumentException]，带中文原因。 */
    fun read(bytes: ByteArray): List<MiniSheet> = try {
        readInternal(bytes).sheets
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("无法解析工作簿文件（不是有效的 xlsx？）", e)
    }

    /** 解析工作簿 + 各表内嵌图片；图片结构异常时按无图处理，不影响表格解析。 */
    fun readWithImages(bytes: ByteArray): MiniWorkbook = try {
        readInternal(bytes)
    } catch (e: IllegalArgumentException) {
        throw e
    } catch (e: Exception) {
        throw IllegalArgumentException("无法解析工作簿文件（不是有效的 xlsx？）", e)
    }

    private fun readInternal(bytes: ByteArray): MiniWorkbook {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) entries[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val parser = newParser()

        // workbook.xml：有序 (表名, rId)
        val workbookBytes = entries["xl/workbook.xml"]
            ?: throw IllegalArgumentException("缺少 xl/workbook.xml")
        val sheetRefs = ArrayList<Pair<String, String>>() // name to rid
        parser.setInput(ByteArrayInputStream(workbookBytes), null)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = attr(parser, "name") ?: continue
                val rid = attr(parser, "id") ?: continue
                sheetRefs.add(name to rid)
            }
        }

        // rels：rId → 文件路径
        val targets = HashMap<String, String>()
        entries["xl/_rels/workbook.xml.rels"]?.let { rels ->
            parser.setInput(ByteArrayInputStream(rels), null)
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = attr(parser, "Id") ?: continue
                    val target = attr(parser, "Target") ?: continue
                    targets[id] = target
                }
            }
        }

        // sharedStrings（Excel/WPS 重存后字符串集中于此）
        val shared = ArrayList<String>()
        entries["xl/sharedStrings.xml"]?.let { sst ->
            parser.setInput(ByteArrayInputStream(sst), null)
            val sb = StringBuilder()
            var inSi = false
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> if (parser.name == "si") { inSi = true; sb.clear() }
                    XmlPullParser.TEXT -> if (inSi) sb.append(parser.text)
                    XmlPullParser.END_TAG -> if (parser.name == "si") { shared.add(sb.toString()); inSi = false }
                }
            }
        }

        val imagesBySheet = HashMap<String, Map<Int, List<ByteArray>>>()
        val sheets = sheetRefs.map { (name, rid) ->
            val target = targets[rid]
                ?: throw IllegalArgumentException("工作表「$name」缺少关系定义")
            val path = when {
                target.startsWith("/xl/") -> target.removePrefix("/")
                target.startsWith("xl/") -> target
                else -> "xl/$target"
            }
            val sheetBytes = entries[path]
                ?: throw IllegalArgumentException("缺少工作表文件 $path")
            imagesBySheet[name] = parseSheetImages(path, sheetBytes, entries, parser)
            MiniSheet(name, parseSheet(sheetBytes, shared, parser))
        }
        return MiniWorkbook(sheets, imagesBySheet.filterValues { it.isNotEmpty() })
    }

    /** 解析某张表的内嵌图片：sheet XML → drawing 关系 → 锚点行 → 媒体字节；任一环缺失即返回空 */
    private fun parseSheetImages(
        sheetPath: String,
        sheetBytes: ByteArray,
        entries: Map<String, ByteArray>,
        parser: XmlPullParser
    ): Map<Int, List<ByteArray>> {
        val result = HashMap<Int, MutableList<ByteArray>>()
        // 1. sheet XML 里的 <drawing r:id="…">
        val drawingRid = run {
            parser.setInput(ByteArrayInputStream(sheetBytes), null)
            var rid: String? = null
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "drawing") {
                    rid = attr(parser, "id"); break
                }
            }
            rid ?: return result
        }
        // 2. 表关系文件：drawing rId → drawing 部件路径
        val sheetDir = sheetPath.substringBeforeLast('/')
        val drawingPath = relTarget(
            entries,
            "$sheetDir/_rels/${sheetPath.substringAfterLast('/')}.rels",
            drawingRid, parser
        )?.let { resolveRelPath(sheetDir, it) } ?: return result
        val drawingBytes = entries[drawingPath] ?: return result
        // 3. drawing 锚点：物理行 → 图片 rId
        val anchors = parseDrawingAnchors(drawingBytes, parser)
        if (anchors.isEmpty()) return result
        val drawingDir = drawingPath.substringBeforeLast('/')
        anchors.forEach { (row, rid) ->
            val target = relTarget(
                entries,
                "$drawingDir/_rels/${drawingPath.substringAfterLast('/')}.rels",
                rid, parser
            ) ?: return@forEach
            val media = entries[resolveRelPath(drawingDir, target)] ?: return@forEach
            result.getOrPut(row) { ArrayList() }.add(media)
        }
        return result
    }

    /** 读取 rels 文件中指定 rId 的 Target；文件缺失或 rId 不存在返回 null */
    private fun relTarget(
        entries: Map<String, ByteArray>,
        relsPath: String,
        rid: String,
        parser: XmlPullParser
    ): String? {
        val relsBytes = entries[relsPath] ?: return null
        parser.setInput(ByteArrayInputStream(relsBytes), null)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                if (attr(parser, "Id") == rid) return attr(parser, "Target")
            }
        }
        return null
    }

    /** 相对 Target 定位（"../media/a.jpg"、"/xl/media/a.jpg"、"media/a.jpg"）→ 包内路径 */
    internal fun resolveRelPath(baseDir: String, target: String): String {
        val t = target.replace('\\', '/')
        if (t.startsWith("/")) return t.removePrefix("/")
        if (t.startsWith("xl/")) return t
        val stack = ArrayDeque<String>()
        (baseDir.split('/') + t.split('/')).forEach { part ->
            when (part) {
                "", "." -> {}
                ".." -> stack.removeLastOrNull()
                else -> stack.addLast(part)
            }
        }
        return stack.joinToString("/")
    }

    /** drawing XML → [(锚点物理行, blip rId)]；oneCellAnchor / twoCellAnchor 都兼容 */
    private fun parseDrawingAnchors(bytes: ByteArray, parser: XmlPullParser): List<Pair<Int, String>> {
        val result = ArrayList<Pair<Int, String>>()
        parser.setInput(ByteArrayInputStream(bytes), null)
        var inFrom = false
        var row = -1
        var embed: String? = null
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "oneCellAnchor", "twoCellAnchor" -> { row = -1; embed = null }
                    "from" -> inFrom = true
                    "row" -> if (inFrom) row = parser.nextText().trim().toIntOrNull() ?: -1
                    "blip" -> embed = attr(parser, "embed")
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "from" -> inFrom = false
                    "oneCellAnchor", "twoCellAnchor" -> {
                        val rid = embed
                        if (row >= 0 && rid != null) result += row to rid
                    }
                }
            }
        }
        return result
    }

    /** 逐行解析 sheetData；单元格稀疏时按 r 属性定位，空位补 null。 */
    private fun parseSheet(bytes: ByteArray, shared: List<String>, parser: XmlPullParser): List<List<String?>> {
        parser.setInput(ByteArrayInputStream(bytes), null)
        val rows = ArrayList<List<String?>>()
        var cells: MutableList<String?>? = null
        var cellType: String? = null
        var vText: StringBuilder? = null
        var inlineText: StringBuilder? = null
        var cellCol = 0
        var colCursor = 0

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> {
                        cells = ArrayList()
                        colCursor = 0
                    }
                    "c" -> {
                        cellType = attr(parser, "t")
                        val ref = attr(parser, "r")
                        cellCol = if (ref != null) colIndexFromRef(ref) else colCursor
                        vText = null
                        inlineText = null
                    }
                    "is" -> inlineText = StringBuilder()
                    "v" -> if (vText == null) vText = StringBuilder()
                }
                XmlPullParser.TEXT -> {
                    if (vText != null) vText.append(parser.text)
                    if (inlineText != null) inlineText.append(parser.text)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "c" -> {
                        val value = when (cellType) {
                            "s" -> vText?.toString()?.trim()?.toIntOrNull()?.let { shared.getOrNull(it) }
                            "inlineStr" -> inlineText?.toString()
                            else -> vText?.toString()
                        }
                        if (cells != null) {
                            while (cells.size <= cellCol) cells.add(null)
                            cells[cellCol] = value
                        }
                        colCursor = cellCol + 1
                        cellType = null
                        vText = null
                        inlineText = null
                    }
                    "row" -> {
                        cells?.let {
                            val rowNum = rows.size + 1
                            while (rows.size < rowNum - 1) rows.add(emptyList())
                            rows.add(it)
                        }
                        cells = null
                    }
                }
            }
        }
        return rows
    }

    private fun newParser(): XmlPullParser =
        XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()

    /** 按本地名取属性（忽略前缀差异：r:id / id 都能取到） */
    private fun attr(parser: XmlPullParser, localName: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == localName) return parser.getAttributeValue(i)
        }
        return null
    }

    /** "C5" → 2（0 基列号） */
    internal fun colIndexFromRef(ref: String): Int {
        var index = 0
        for (ch in ref) {
            if (ch in 'A'..'Z') index = index * 26 + (ch - 'A' + 1)
            else if (ch in 'a'..'z') index = index * 26 + (ch - 'a' + 1)
            else break
        }
        return index - 1
    }
}
