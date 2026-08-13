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
        String storage = row.StorageLocation?.toString()?.trim()

        if (product && plant && storage) {

            String key = "${product}|${plant}|${storage}"
            cache[key] = true
        }
    }

    message.setProperty(
        "StorageLocationCache",
        JsonOutput.toJson(cache)
    )

    return message
}