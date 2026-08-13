import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {

    def originalBody = message.getProperty("OriginalValidationXML")

    if (originalBody != null) {
        message.setBody(originalBody)
    }

    return message
}
