import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlSlurper

def Message processData(Message message) {
    String body = message.getBody(String) ?: ""
    String documentType = message.getProperty("DocumentType")?.toString()?.trim() ?: ""

    boolean valid = false
    List<String> returnedRows = []

    if (documentType) {
        try {
            def xml = new XmlSlurper(false, false).parseText(body)

            // RFC_READ_TABLE returns selected row data in DATA/item/WA.
            // Using depthFirst keeps this robust even if namespaces/wrappers vary.
            returnedRows = xml.depthFirst()
                    .findAll { it.name() == "WA" }
                    .collect { it.text()?.trim() ?: "" }
                    .findAll { !it.isEmpty() }

            // We request only BSART, so WA should normally be exactly the document type.
            // The split fallback also works if a delimiter is present.
            valid = returnedRows.any { String wa ->
                wa.equalsIgnoreCase(documentType) ||
                wa.split(/\|/, -1).any { it?.trim()?.equalsIgnoreCase(documentType) }
            }
        } catch (Exception e) {
            message.setProperty("DocumentTypeValid", "false")
            message.setProperty("DocumentTypeExists", "false")
            message.setProperty("DocumentTypeRFCResult", body)
            message.setProperty("DocumentTypeError", "Could not parse RFC_READ_TABLE response for DocumentType ${documentType}: ${e.message}")
            return message
        }
    }

    message.setProperty("DocumentTypeValid", valid.toString())
    message.setProperty("DocumentTypeExists", valid.toString())
    message.setProperty("DocumentTypeRFCResult", returnedRows.join(" || "))

    if (!documentType) {
        message.setProperty("DocumentTypeError", "DocumentType is required")
    } else if (!valid) {
        message.setProperty("DocumentTypeError", "DocumentType ${documentType} is not configured as a Purchase Order document type in T161")
    } else {
        message.setProperty("DocumentTypeError", "")
    }

    // Keep RFC response in the body. Restore OriginalRecord in the next Content Modifier.
    return message
}
