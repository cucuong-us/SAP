import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.Node
import groovy.util.XmlParser
import groovy.xml.XmlUtil

def Message processData(Message message) {

    String body = message.getBody(String) ?: ""

    if (!body.trim()) {
        message.setBody("<FailedRows/>")
        message.setProperty("FailedRowCount", 0)
        message.setProperty("HasFailedRows", false)
        return message
    }

    def root = new XmlParser(false, false).parseText(body)

    def localName = { Node n ->
        String name = n.name().toString()
        return name.contains(":") ? name.substring(name.lastIndexOf(":") + 1) : name
    }

    List<Node> rowResults = []
    root.depthFirst().each { obj ->
        if (obj instanceof Node && localName(obj) == "RowResult") {
            rowResults << (Node) obj
        }
    }

    List<Node> failedRows = rowResults.findAll { Node row ->
        Node statusNode = row.children().find { child ->
            child instanceof Node && localName((Node) child) == "Status"
        } as Node

        String status = statusNode?.text()?.trim() ?: ""
        return status.equalsIgnoreCase("ERROR")
    }

    String failedXml = failedRows.collect { Node row ->
        XmlUtil.serialize(row)
            .replaceFirst(/(?s)^\s*<\?xml[^?]*\?>\s*/, "")
            .trim()
    }.join("\n")

    String output = failedRows
        ? "<FailedRows>\n${failedXml}\n</FailedRows>"
        : "<FailedRows/>"

    message.setBody(output)
    message.setProperty("FailedRowCount", failedRows.size())
    message.setProperty("HasFailedRows", !failedRows.isEmpty())

    return message
}
