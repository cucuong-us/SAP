import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.MarkupBuilder
import groovy.util.XmlSlurper

import java.util.zip.ZipInputStream

Message processData(Message message) {

    byte[] excelBytes = message.getBody(byte[].class)

    if (excelBytes == null || excelBytes.length == 0) {
        throw new IllegalArgumentException("The Excel payload is empty.")
    }

    Map<String, byte[]> excelEntries = readExcelEntries(excelBytes)

    if (excelEntries.isEmpty()) {
        throw new IllegalArgumentException(
            "The payload is not a valid .xlsx file or contains no ZIP entries."
        )
    }

    List<String> sharedStrings = readSharedStrings(
        excelEntries["xl/sharedStrings.xml"]
    )

    byte[] worksheetBytes = excelEntries["xl/worksheets/sheet1.xml"]

    if (worksheetBytes == null) {
        throw new IllegalArgumentException(
            "The first Excel worksheet could not be found at xl/worksheets/sheet1.xml."
        )
    }

    List<List<String>> rows = readWorksheet(
        worksheetBytes,
        sharedStrings
    )

    if (rows.isEmpty()) {
        throw new IllegalArgumentException(
            "The first worksheet contains no readable rows. Check that the file is a real .xlsx file and that data exists in the first sheet."
        )
    }

    List<String> headers = rows[0].collect {
        normalizeHeader(it)
    }

    List<String> mandatoryHeaders = [
        "Vendor",
        "Material",
        "Plant",
        "Qty",
        "Price"
    ]

    mandatoryHeaders.each { requiredHeader ->
        if (!headers.contains(requiredHeader)) {
            throw new IllegalArgumentException(
                "Missing Excel column: ${requiredHeader}. Found headers: ${headers}"
            )
        }
    }

    StringWriter output = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(output)

    xml.PurchaseOrders {
        rows.drop(1).each { row ->

            Map<String, String> record = [:]

            headers.eachWithIndex { header, index ->
                record[header] =
                    index < row.size() ? (row[index] ?: "").trim() : ""
            }

            boolean emptyRow = record.values().every {
                it == null || it.trim().isEmpty()
            }

            if (!emptyRow) {
                Item {
                    Vendor(record["Vendor"])
                    Material(record["Material"])
                    Plant(record["Plant"])
                    Quantity(record["Qty"])
                    Price(record["Price"])
                }
            }
        }
    }

    message.setBody(output.toString())
    message.setHeader("Content-Type", "application/xml")

    return message
}

Map<String, byte[]> readExcelEntries(byte[] excelBytes) {

    Map<String, byte[]> entries = [:]

    ZipInputStream zipInputStream =
        new ZipInputStream(new ByteArrayInputStream(excelBytes))

    def zipEntry

    while ((zipEntry = zipInputStream.nextEntry) != null) {

        ByteArrayOutputStream entryOutput =
            new ByteArrayOutputStream()

        byte[] buffer = new byte[4096]
        int bytesRead

        while ((bytesRead = zipInputStream.read(buffer)) > 0) {
            entryOutput.write(buffer, 0, bytesRead)
        }

        entries[zipEntry.name] = entryOutput.toByteArray()
        zipInputStream.closeEntry()
    }

    zipInputStream.close()
    return entries
}

List<String> readSharedStrings(byte[] sharedStringsBytes) {

    List<String> values = []

    if (sharedStringsBytes == null) {
        return values
    }

    def xml = new XmlSlurper(false, false).parse(
        new ByteArrayInputStream(sharedStringsBytes)
    )

    xml.depthFirst()
        .findAll { node -> node.name() == "si" }
        .each { sharedString ->

            String value = sharedString.depthFirst()
                .findAll { node -> node.name() == "t" }
                .collect { node -> node.text() }
                .join("")

            values.add(value)
        }

    return values
}

List<List<String>> readWorksheet(
    byte[] worksheetBytes,
    List<String> sharedStrings
) {

    List<List<String>> result = []

    def xml = new XmlSlurper(false, false).parse(
        new ByteArrayInputStream(worksheetBytes)
    )

    def excelRows = xml.depthFirst().findAll {
        node -> node.name() == "row"
    }

    excelRows.each { excelRow ->

        Map<Integer, String> cellValues = [:]
        int maximumColumn = -1

        def cells = excelRow.children().findAll {
            node -> node.name() == "c"
        }

        cells.each { cell ->

            String cellReference = cell.attributes()["r"]?.toString() ?: ""
            int columnIndex = getColumnIndex(cellReference)

            String cellType = cell.attributes()["t"]?.toString() ?: ""
            String value = ""

            if (cellType == "s") {

                String sharedStringIndex = firstChildText(cell, "v")

                if (sharedStringIndex) {
                    int index = sharedStringIndex as int

                    if (index >= 0 && index < sharedStrings.size()) {
                        value = sharedStrings[index]
                    }
                }

            } else if (cellType == "inlineStr") {

                value = cell.depthFirst()
                    .findAll { node -> node.name() == "t" }
                    .collect { node -> node.text() }
                    .join("")

            } else {

                value = firstChildText(cell, "v")
            }

            cellValues[columnIndex] = value
            maximumColumn = Math.max(maximumColumn, columnIndex)
        }

        if (maximumColumn >= 0) {
            List<String> row = []

            for (int index = 0; index <= maximumColumn; index++) {
                row.add(cellValues[index] ?: "")
            }

            result.add(row)
        }
    }

    return result
}

String firstChildText(def parent, String childName) {
    def child = parent.children().find {
        node -> node.name() == childName
    }

    return child ? child.text() : ""
}

int getColumnIndex(String cellReference) {

    String columnLetters =
        cellReference.replaceAll("[^A-Za-z]", "").toUpperCase()

    if (!columnLetters) {
        return 0
    }

    int columnIndex = 0

    columnLetters.each { character ->
        columnIndex =
            columnIndex * 26 + ((int) character - (int) 'A' + 1)
    }

    return columnIndex - 1
}

String normalizeHeader(String header) {

    String normalized = header?.trim()

    Map<String, String> supportedHeaders = [
        "Vendor"   : "Vendor",
        "Material" : "Material",
        "Plant"    : "Plant",
        "Qty"      : "Qty",
        "Quantity" : "Qty",
        "Price"    : "Price"
    ]

    return supportedHeaders[normalized] ?: normalized
}
