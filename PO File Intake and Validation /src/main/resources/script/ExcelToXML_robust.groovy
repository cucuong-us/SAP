import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.MarkupBuilder

import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import java.util.zip.ZipInputStream

Message processData(Message message) {

    byte[] excelBytes = message.getBody(byte[].class)

    if (excelBytes == null || excelBytes.length == 0) {
        throw new IllegalArgumentException(
            "The Excel payload is empty."
        )
    }

    Map<String, byte[]> entries = unzip(excelBytes)

    byte[] worksheetBytes =
        entries["xl/worksheets/sheet1.xml"]

    if (worksheetBytes == null) {
        throw new IllegalArgumentException(
            "Cannot find xl/worksheets/sheet1.xml in the .xlsx file."
        )
    }

    List<String> sharedStrings = parseSharedStrings(
        entries["xl/sharedStrings.xml"]
    )

    List<List<String>> rows = parseWorksheet(
        worksheetBytes,
        sharedStrings
    )

    if (rows.isEmpty()) {
        throw new IllegalArgumentException(
            "No rows were found in sheet1.xml. " +
            "The payload may not be the expected Excel file."
        )
    }

    List<String> headers = rows[0].collect {
        normalizeHeader(it)
    }

    List<String> requiredHeaders = [
        "Vendor",
        "Material",
        "Plant",
        "Quantity",
        "Price",
        "CompanyCode",
        "DocumentType",
        "PurchasingOrganization",
        "PurchasingGroup",
        "Currency",
        "DocumentDate",
        "POItem",
        "StorageLocation",
        "POUnit",
        "PriceUnit"
    ]

    requiredHeaders.each { required ->

        if (!headers.contains(required)) {
            throw new IllegalArgumentException(
                "Missing Excel column '${required}'. " +
                "Found headers: ${headers}"
            )
        }
    }

    StringWriter writer = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(writer)

    xml.PurchaseOrders {

        rows.drop(1).each { row ->

            Map<String, String> record = [:]

            headers.eachWithIndex { header, index ->

                record[header] =
                    index < row.size()
                        ? (row[index] ?: "").trim()
                        : ""
            }

            boolean emptyRow = record.values().every {
                it == null || it.trim().isEmpty()
            }

            if (!emptyRow) {

                Item {
                    Vendor(record["Vendor"])
                    Material(record["Material"])
                    Plant(record["Plant"])
                    Quantity(record["Quantity"])
                    Price(record["Price"])

                    CompanyCode(record["CompanyCode"])
                    DocumentType(record["DocumentType"])

                    PurchasingOrganization(
                        record["PurchasingOrganization"]
                    )

                    PurchasingGroup(
                        record["PurchasingGroup"]
                    )

                    Currency(record["Currency"])
                    DocumentDate(record["DocumentDate"])
                    POItem(record["POItem"])

                    StorageLocation(
                        record["StorageLocation"]
                    )

                    POUnit(record["POUnit"])
                    PriceUnit(record["PriceUnit"])
                }
            }
        }
    }

    message.setBody(writer.toString())
    message.setHeader(
        "Content-Type",
        "application/xml"
    )

    return message
}

Map<String, byte[]> unzip(byte[] data) {

    Map<String, byte[]> entries = [:]

    ZipInputStream zip = new ZipInputStream(
        new ByteArrayInputStream(data)
    )

    def entry
    byte[] buffer = new byte[8192]

    while ((entry = zip.nextEntry) != null) {

        ByteArrayOutputStream output =
            new ByteArrayOutputStream()

        int length

        while ((length = zip.read(buffer)) != -1) {
            output.write(buffer, 0, length)
        }

        entries[entry.name] = output.toByteArray()

        zip.closeEntry()
    }

    zip.close()

    return entries
}

List<String> parseSharedStrings(byte[] xmlBytes) {

    List<String> values = []

    if (xmlBytes == null) {
        return values
    }

    XMLStreamReader reader = createReader(xmlBytes)

    boolean insideStringItem = false
    StringBuilder current = new StringBuilder()

    while (reader.hasNext()) {

        int event = reader.next()

        if (event == XMLStreamConstants.START_ELEMENT) {

            if (reader.localName == "si") {

                insideStringItem = true
                current.setLength(0)

            } else if (
                insideStringItem &&
                reader.localName == "t"
            ) {
                current.append(
                    reader.elementText
                )
            }

        } else if (
            event == XMLStreamConstants.END_ELEMENT &&
            reader.localName == "si"
        ) {

            values.add(current.toString())
            insideStringItem = false
        }
    }

    reader.close()

    return values
}

List<List<String>> parseWorksheet(
    byte[] xmlBytes,
    List<String> sharedStrings
) {

    List<List<String>> rows = []

    XMLStreamReader reader = createReader(xmlBytes)

    Map<Integer, String> currentRow = null
    int maximumColumn = -1

    String currentCellReference = null
    String currentCellType = null
    String currentValue = ""

    boolean insideCell = false

    while (reader.hasNext()) {

        int event = reader.next()

        if (event == XMLStreamConstants.START_ELEMENT) {

            String name = reader.localName

            if (name == "row") {

                currentRow = [:]
                maximumColumn = -1

            } else if (
                name == "c" &&
                currentRow != null
            ) {

                insideCell = true

                currentCellReference =
                    reader.getAttributeValue(null, "r")

                currentCellType =
                    reader.getAttributeValue(null, "t")

                currentValue = ""

            } else if (
                insideCell &&
                (name == "v" || name == "t")
            ) {

                currentValue =
                    reader.elementText ?: ""
            }

        } else if (
            event == XMLStreamConstants.END_ELEMENT
        ) {

            String name = reader.localName

            if (
                name == "c" &&
                insideCell &&
                currentRow != null
            ) {

                int columnIndex =
                    getColumnIndex(
                        currentCellReference
                    )

                String finalValue =
                    currentValue

                if (
                    currentCellType == "s" &&
                    currentValue
                ) {

                    int index =
                        currentValue as int

                    if (
                        index >= 0 &&
                        index < sharedStrings.size()
                    ) {
                        finalValue =
                            sharedStrings[index]
                    }
                }

                currentRow[columnIndex] =
                    finalValue

                maximumColumn =
                    Math.max(
                        maximumColumn,
                        columnIndex
                    )

                insideCell = false
                currentCellReference = null
                currentCellType = null
                currentValue = ""

            } else if (
                name == "row" &&
                currentRow != null
            ) {

                List<String> row = []

                for (
                    int i = 0;
                    i <= maximumColumn;
                    i++
                ) {
                    row.add(
                        currentRow[i] ?: ""
                    )
                }

                if (!row.isEmpty()) {
                    rows.add(row)
                }

                currentRow = null
                maximumColumn = -1
            }
        }
    }

    reader.close()

    return rows
}

XMLStreamReader createReader(
    byte[] xmlBytes
) {

    XMLInputFactory factory =
        XMLInputFactory.newFactory()

    factory.setProperty(
        XMLInputFactory.IS_NAMESPACE_AWARE,
        true
    )

    return factory.createXMLStreamReader(
        new ByteArrayInputStream(xmlBytes)
    )
}

int getColumnIndex(
    String cellReference
) {

    if (!cellReference) {
        return 0
    }

    String letters =
        cellReference
            .replaceAll(
                "[^A-Za-z]",
                ""
            )
            .toUpperCase()

    int index = 0

    letters.each { character ->

        index =
            index * 26 +
            (
                (int) character -
                (int) 'A' +
                1
            )
    }

    return index - 1
}

String normalizeHeader(
    String header
) {

    String normalized =
        header?.trim()

    Map<String, String> aliases = [
        "Vendor"                 : "Vendor",
        "Material"               : "Material",
        "Plant"                  : "Plant",

        "Qty"                    : "Quantity",
        "Quantity"               : "Quantity",

        "Price"                  : "Price",

        "CompanyCode"            : "CompanyCode",
        "DocumentType"           : "DocumentType",

        "PurchasingOrganization" : "PurchasingOrganization",
        "PurchasingGroup"        : "PurchasingGroup",

        "Currency"               : "Currency",
        "DocumentDate"           : "DocumentDate",
        "POItem"                 : "POItem",

        "StorageLocation"        : "StorageLocation",
        "POUnit"                 : "POUnit",
        "PriceUnit"              : "PriceUnit"
    ]

    return aliases[normalized] ?: normalized
}