import com.sap.gateway.ip.core.customdev.util.Message

def Message WrapGatheredResults(Message message) {

    String body = message.getBody(String) ?: ""

    body = body.replaceAll(
        /<\?xml[^?]*\?>/,
        ""
    ).trim()

    String wrappedBody =
        "<ValidationResults>" +
        body +
        "</ValidationResults>"

    message.setBody(wrappedBody)

    return message
}