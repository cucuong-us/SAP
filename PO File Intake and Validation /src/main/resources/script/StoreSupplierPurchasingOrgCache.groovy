import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def Message processData(Message message) {


    def json = new JsonSlurper().parse(
        message.getBody(java.io.Reader)
        )

    def cache = [:]

    json?.d?.results?.each { row ->

        def supplier = row.Supplier?.toString()?.trim()
        def purchasingOrganization = row.PurchasingOrganization?.toString()?.trim()

        if (supplier && purchasingOrganization) {
            def key = "${supplier}|${purchasingOrganization}"
            cache[key] = true
        }
    }

    message.setProperty(
        "SupplierPurchasingOrgCache",
        JsonOutput.toJson(cache)
    )

    return message
}
