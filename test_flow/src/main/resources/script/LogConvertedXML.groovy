import com.sap.gateway.ip.core.customdev.util.Message

Message processData(Message message) {

    String body = message.getBody(String)

    def messageLog = messageLogFactory.getMessageLog(message)

    if (messageLog != null) {
        messageLog.addAttachmentAsString(
            "Converted_Excel_XML",
            body,
            "application/xml"
        )
    }

    return message
}
