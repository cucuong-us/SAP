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
        String valuationArea = row.ValuationArea?.toString()?.trim()

        if (product && valuationArea) {

            String key = "${product}|${valuationArea}"

            cache[key] = [
                valuationType       : row.ValuationType?.toString()?.trim() ?: "",
                valuationClass      : row.ValuationClass?.toString()?.trim() ?: "",
                standardPrice       : row.StandardPrice?.toString()?.trim() ?: "",
                movingAveragePrice  : row.MovingAveragePrice?.toString()?.trim() ?: "",
                priceUnitQty        : row.PriceUnitQty?.toString()?.trim() ?: "",
                currency            : row.Currency?.toString()?.trim() ?: "",
                baseUnit            : row.BaseUnit?.toString()?.trim() ?: "",
                isMarkedForDeletion : row.IsMarkedForDeletion ?: false
            ]
        }
    }

    message.setProperty(
        "ValuationCache",
        JsonOutput.toJson(cache)
    )

    return message
}