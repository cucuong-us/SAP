import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {

    String error = message.getProperty("IDocStatusText") as String ?: ""

    String safe = error
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    message.setProperty("IDocStatusTextXmlSafe", safe)

    return message
}
