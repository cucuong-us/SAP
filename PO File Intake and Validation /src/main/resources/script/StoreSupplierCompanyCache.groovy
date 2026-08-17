import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

def Message processData(Message message) {

    
    def json = new JsonSlurper().parse(
        message.getBody(java.io.Reader)
        )

    def cache = [:]

    json?.d?.results?.each { row ->

        def supplier    = row.Supplier?.toString()?.trim()
        def companyCode = row.CompanyCode?.toString()?.trim()

        if (supplier && companyCode) {
            def key = "${supplier}|${companyCode}"
            cache[key] = true
        }
    }

    message.setProperty(
        "SupplierCompanyCache",
        JsonOutput.toJson(cache)
    )

    return message
}
