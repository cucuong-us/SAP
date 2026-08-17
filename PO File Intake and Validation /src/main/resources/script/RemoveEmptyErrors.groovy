import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.XmlUtil

def Message processData(Message message) {

    String body = message.getBody(String) ?: ""

    if (!body.trim()) {
        throw new IllegalArgumentException(
            "Payload before IDoc mapping is empty."
        )
    }

    def root

    try {
        root = new XmlSlurper(false, false).parseText(body)
    } catch (Exception e) {
        throw new IllegalArgumentException(
            "Payload is not valid XML: ${e.message}",
            e
        )
    }

    def validationResult

    if (root.name() == "ValidationResults") {

        if (root.ValidationResult.size() == 0) {
            throw new IllegalArgumentException(
                "ValidationResults does not contain any ValidationResult."
            )
        }

        validationResult = root.ValidationResult[0]

    } else if (root.name() == "ValidationResult") {

        validationResult = root

    } else {

        throw new IllegalArgumentException(
            "Unexpected root element: ${root.name()}"
        )
    }

    /*
     * Remove <Errors/> only when it is completely empty.
     */
    validationResult.Errors.findAll { errorsNode ->

        errorsNode.children().size() == 0 &&
        errorsNode.text().trim().isEmpty()

    }.replaceNode {}

    /*
     * Get the PurchaseOrders structure required by
     * the Message Mapping source XSD.
     */
    def purchaseOrders = validationResult.PurchaseOrders

    if (
        purchaseOrders == null ||
        purchaseOrders.size() == 0
    ) {
        throw new IllegalArgumentException(
            "PurchaseOrders element was not found inside ValidationResult."
        )
    }

    if (purchaseOrders.Item.size() == 0) {
        throw new IllegalArgumentException(
            "PurchaseOrders does not contain an Item element."
        )
    }

    /*
     * Convert only <PurchaseOrders> back to XML.
     */
    String result = XmlUtil.serialize(
        purchaseOrders
    )

    /*
     * Remove XML declaration:
     * <?xml version="1.0" encoding="UTF-8"?>
     */
    result = result
        .replaceFirst(/<\?xml.*?\?>/, "")
        .trim()

    message.setBody(result)
    message.setHeader(
        "Content-Type",
        "application/xml"
    )

    return message
}