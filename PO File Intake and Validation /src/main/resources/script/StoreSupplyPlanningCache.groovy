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
        def plant   = row.Plant?.toString()?.trim()

        if (product && plant) {

            def key = "${product}|${plant}"

            cache[key] = [
                procurementType    : row.ProcurementType?.toString()?.trim() ?: "",
                procurementSubType : row.ProcurementSubType?.toString()?.trim() ?: "",
                defaultStorage     : row.DfltStorageLocationExtProcmt?.toString()?.trim() ?: ""
            ]
        }
    }

    message.setProperty(
        "SupplyPlanningCache",
        JsonOutput.toJson(cache)
    )

    return message
}
