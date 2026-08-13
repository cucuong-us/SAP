import com.sap.gateway.ip.core.customdev.util.Message
import java.util.UUID

Message processData(Message message) {

    String body = message.getBody(String) ?: ""

    if (!body.trim()) {
        throw new IllegalArgumentException(
            "Payload is empty after Excel-to-XML conversion."
        )
    }

    Node root

    try {
        root = new XmlParser(false, false).parseText(body)
    } catch (Exception e) {
        throw new IllegalArgumentException(
            "Payload is not valid XML: ${e.message}",
            e
        )
    }

    def localName = { Object nodeName ->
        String name = String.valueOf(nodeName)

        if (name.contains("}")) {
            return name.substring(name.lastIndexOf("}") + 1)
        }

        if (name.contains(":")) {
            return name.substring(name.indexOf(":") + 1)
        }

        return name
    }

    List<Node> items = root.children().findAll { child ->
        child instanceof Node &&
        localName(child.name()) == "Item"
    } as List<Node>

    if (items.isEmpty()) {
        throw new IllegalArgumentException(
            "No /PurchaseOrders/Item records were found."
        )
    }

    String originalFileName =
        (message.getProperty("OriginalFileName") ?: "")
            .toString()
            .trim()

    if (!originalFileName) {
        originalFileName =
            (message.getHeader("CamelFileName", String)
                ?: "PurchaseOrder.xlsx")
                .trim()
    }

    String baseName =
        originalFileName.replaceFirst(/\.[^.]+$/, "")

    baseName =
        baseName.replaceAll(/[^A-Za-z0-9_-]/, "_")

    if (baseName.length() > 60) {
        baseName = baseName.substring(0, 60)
    }

    String timestamp =
        new Date().format(
            "yyyyMMddHHmmssSSS",
            TimeZone.getTimeZone("UTC")
        )

    String shortUuid =
        UUID.randomUUID()
            .toString()
            .replace("-", "")
            .substring(0, 8)

    String batchId =
        "${baseName}_${timestamp}_${shortUuid}"

    /*
     * Important:
     * This script does NOT add RowNumber into the XML payload.
     * The original XML structure remains unchanged so that
     * downstream validation and message mapping continue to work.
     *
     * RowNumber should be created after the General Splitter
     * from CamelSplitIndex in SetRowContext.groovy.
     */

    message.setProperty(
        "BatchId",
        batchId
    )

    message.setProperty(
        "ExpectedRowCount",
        String.valueOf(items.size())
    )

    message.setProperty(
        "DispatchComplete",
        "false"
    )

    message.setProperty(
        "BatchStatus",
        "SENDING"
    )

    message.setProperty(
        "Finalized",
        "false"
    )

    message.setProperty(
        "OriginalFileName",
        originalFileName
    )

    return message
}
