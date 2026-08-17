import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def Message processData(Message message) {

    
    def json = new JsonSlurper().parse(
        message.getBody(java.io.Reader)
        )

    def cache = [:]

    json?.d?.results?.each { row ->

        def product = row.Product?.toString()?.trim()
        def unit    = row.AlternativeUnit?.toString()?.trim()

        if (product && unit) {
            def key = "${product}|${unit}"
            cache[key] = true
        }
    }

    message.setProperty(
        "POUnitCache",
        JsonOutput.toJson(cache)
    )

    return message
}
