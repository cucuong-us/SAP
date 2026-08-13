import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {

    String originalRecord = message.getProperty("OriginalRecord") as String

    if (originalRecord == null) {
        throw new IllegalStateException("OriginalRecord property is missing")
    }

    // Remove XML declaration only from the saved row property.
    // Keep the current message body unchanged.
    originalRecord = originalRecord.replaceFirst(
        /(?is)^\s*<\?xml[^?]*\?>\s*/,
        ""
    )

    message.setProperty("OriginalRecord", originalRecord)

    return message
}
