import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {

    def body = message.getBody(String)

    message.setProperty("OriginalValidationXML", body)

    return message
}
