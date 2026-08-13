import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {

    String body = message.getBody(String) ?: ""

    // Remove XML declaration
    body = body.replaceFirst(/<\?xml.*?\?>/, "").trim()

    message.setBody(body)

    return message
}
