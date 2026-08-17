import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.MarkupBuilder

import java.util.zip.ZipInputStream

Message processData(Message message) {

    byte[] excelBytes = message.getBody(byte[].class)

    if (excelBytes == null || excelBytes.length == 0) {
        throw new IllegalArgumentException("The Excel payload is empty.")
    }

    Map<String, byte[]> excelEntries = readExcelEntries(excelBytes)

    List<String> sharedStrings = readSharedStrings(
        excelEntries["xl/sharedStrings.xml"]
    )

    byte[] worksheetBytes = excelEntries["xl/worksheets/sheet1.xml"]

    if (worksheetBytes == null) {
        throw new IllegalArgumentException(
            "The first Excel worksheet could not be found."
        )
    }

    List<List<String>> rows = readWorksheet(
        worksheetBytes,
        sharedStrings
    )

    if (rows.isEmpty()) {
        throw new IllegalArgumentException(
            "The Excel worksheet contains no data."
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
                "Missing Excel column: ${requiredHeader}"
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
                    index < row.size() ? row[index]?.trim() : ""
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

    def sharedStringsXml = new XmlSlurper(false, false).parse(
        new ByteArrayInputStream(sharedStringsBytes)
    )

    sharedStringsXml.si.each { sharedString ->

        String value = ""

        if (sharedString.t.size() > 0) {
            value = sharedString.t.text()
        } else {
            value = sharedString.r.collect {
                it.t.text()
            }.join("")
        }

        values.add(value)
    }

    return values
}

List<List<String>> readWorksheet(
    byte[] worksheetBytes,
    List<String> sharedStrings
) {

    List<List<String>> result = []

    def worksheetXml = new XmlSlurper(false, false).parse(
        new ByteArrayInputStream(worksheetBytes)
    )

    worksheetXml.sheetData.row.each { excelRow ->

        Map<Integer, String> cellValues = [:]
        int maximumColumn = -1

        excelRow.c.each { cell ->

            String cellReference = cell.@r.text()
            int columnIndex = getColumnIndex(cellReference)

            String cellType = cell.@t.text()
            String value = ""

            if (cellType == "s") {

                String sharedStringIndex = cell.v.text()

                if (sharedStringIndex) {
                    int index = sharedStringIndex as int

                    if (index < sharedStrings.size()) {
                        value = sharedStrings[index]
                    }
                }

            } else if (cellType == "inlineStr") {

                value = cell.is.t.text()

            } else {

                value = cell.v.text()
            }

            cellValues[columnIndex] = value
            maximumColumn = Math.max(maximumColumn, columnIndex)
        }

        List<String> row = []

        for (int index = 0; index <= maximumColumn; index++) {
            row.add(cellValues[index] ?: "")
        }

        result.add(row)
    }

    return result
}

int getColumnIndex(String cellReference) {

    String columnLetters =
        cellReference.replaceAll("[^A-Za-z]", "").toUpperCase()

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
