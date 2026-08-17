import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.Node
import groovy.util.XmlParser

import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.text.SimpleDateFormat
import java.util.TimeZone

def Message processData(Message message) {

    String body = message.getBody(String) ?: ""
    if (!body.trim()) {
        throw new IllegalStateException("BuildErrorExcel: payload is empty")
    }

    def root = new XmlParser(false, false).parseText(body)

    def localName = { Node n ->
        def nm = n.name()
        try {
            if (nm.metaClass?.hasProperty(nm, "localPart")) {
                return nm.localPart?.toString()
            }
        } catch (ignored) {}
        String s = nm.toString()
        if (s.contains("}")) s = s.substring(s.lastIndexOf("}") + 1)
        if (s.contains(":")) s = s.substring(s.lastIndexOf(":") + 1)
        return s
    }

    def childNode = { Node parent, String wanted ->
        parent.children().find {
            it instanceof Node && localName((Node) it) == wanted
        } as Node
    }

    List<Node> rowResults = []
    root.depthFirst().each { obj ->
        if (obj instanceof Node && localName((Node) obj) == "RowResult") {
            rowResults << (Node) obj
        }
    }

    if (rowResults.isEmpty()) {
        throw new IllegalStateException("BuildErrorExcel: no RowResult found")
    }

    LinkedHashSet<String> originalColumns = new LinkedHashSet<String>()

    rowResults.each { Node row ->
        Node data = childNode(row, "Data")
        if (data != null) {
            Node item = null
            data.depthFirst().each { obj ->
                if (item == null && obj instanceof Node && localName((Node) obj) == "Item") {
                    item = (Node) obj
                }
            }
            if (item != null) {
                item.children().each { obj ->
                    if (obj instanceof Node) {
                        originalColumns.add(localName((Node) obj))
                    }
                }
            }
        }
    }

    List<String> headers = []
    headers.addAll(originalColumns)
    headers.add("ErrorStage")
    headers.add("ErrorMessage")
    headers.add("IDocNumber")

    List<List<String>> dataRows = []

    rowResults.each { Node row ->
        Map<String, String> values = [:]

        values["ErrorStage"] = childNode(row, "ErrorStage")?.text()?.trim() ?: ""
        values["ErrorMessage"] = childNode(row, "ErrorMessage")?.text()?.trim() ?: ""
        values["IDocNumber"] = childNode(row, "IDocNumber")?.text()?.trim() ?: ""

        Node data = childNode(row, "Data")
        if (data != null) {
            Node item = null
            data.depthFirst().each { obj ->
                if (item == null && obj instanceof Node && localName((Node) obj) == "Item") {
                    item = (Node) obj
                }
            }
            if (item != null) {
                item.children().each { obj ->
                    if (obj instanceof Node) {
                        Node n = (Node) obj
                        values[localName(n)] = n.text()?.trim() ?: ""
                    }
                }
            }
        }

        dataRows << headers.collect { h -> values[h] ?: "" }
    }

    def xmlEscape = { String s ->
        (s ?: "")
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    def columnName = { int number ->
        int n = number
        String result = ""
        while (n > 0) {
            n--
            result = ((char)('A'.charAt(0) + (n % 26))).toString() + result
            n = (int)(n / 26)
        }
        return result
    }

    StringBuilder sheet = new StringBuilder()
    sheet << '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    sheet << '<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
    sheet << '<sheetData>'

    List<List<String>> allRows = [headers] + dataRows

    allRows.eachWithIndex { List<String> rowValues, int rIdx ->
        int rowNum = rIdx + 1
        sheet << '<row r="' << rowNum << '">'
        rowValues.eachWithIndex { String value, int cIdx ->
            String ref = columnName(cIdx + 1) + rowNum
            sheet << '<c r="' << ref << '" t="inlineStr"><is><t xml:space="preserve">'
            sheet << xmlEscape(value)
            sheet << '</t></is></c>'
        }
        sheet << '</row>'
    }

    sheet << '</sheetData>'
    sheet << '</worksheet>'

    String contentTypes = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>'''

    String rootRels = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>'''

    String workbook = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
          xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Failed Rows" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>'''

    String workbookRels = '''<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
</Relationships>'''

    ByteArrayOutputStream baos = new ByteArrayOutputStream()
    ZipOutputStream zos = new ZipOutputStream(baos)

    def addEntry = { String name, String text ->
        zos.putNextEntry(new ZipEntry(name))
        byte[] bytes = text.getBytes("UTF-8")
        zos.write(bytes, 0, bytes.length)
        zos.closeEntry()
    }

    addEntry("[Content_Types].xml", contentTypes)
    addEntry("_rels/.rels", rootRels)
    addEntry("xl/workbook.xml", workbook)
    addEntry("xl/_rels/workbook.xml.rels", workbookRels)
    addEntry("xl/worksheets/sheet1.xml", sheet.toString())

    zos.finish()
    zos.close()

    byte[] xlsx = baos.toByteArray()
    message.setBody(xlsx)

    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss")
    sdf.setTimeZone(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
    String fileName = "PurchaseOrder_ERROR_" + sdf.format(new Date()) + ".xlsx"

    message.setProperty("ErrorFileName", fileName)
    message.setHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    message.setHeader("CamelFileName", fileName)

    return message
}