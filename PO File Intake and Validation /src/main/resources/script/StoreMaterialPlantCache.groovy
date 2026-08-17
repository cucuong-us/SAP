import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def Message processData(Message message) {

    def json = new JsonSlurper().parse(
        message.getBody(java.io.Reader)
    )

    def cache = [:]

    json?.d?.results?.each { row ->

        String product = row.Product?.toString()?.trim()
        String plant = row.Plant?.toString()?.trim()

        if (product && plant) {
            String key = "${product}|${plant}"
            cache[key] = true
        }
    }

    // Save cache into Exchange Property
    message.setProperty(
        "MaterialPlantCache",
        JsonOutput.toJson(cache)
    )

    return message
}