import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper

def Message processData(Message message) {

    // Current body is the JSON response returned by the Material/Plant API
    String body = message.getBody(String)

    // Values previously stored in PrepareValidation
    String material =
        message.getProperty("Material")?.toString()?.trim()

    String plant =
        message.getProperty("Plant")?.toString()?.trim()

    boolean materialPlantExists = false
    String materialPlantError = ""

    try {

        if (body == null || body.trim().isEmpty()) {

            materialPlantError =
                "Material/Plant API returned an empty response for material ${material}, plant ${plant}"

        } else {

            def json = new JsonSlurper().parseText(body)

            // Expected OData V2 response:
            // { "d": { "results": [...] } }
            def results = json?.d?.results

            if (results instanceof Collection && !results.isEmpty()) {

                materialPlantExists = results.any { result ->

                    String returnedProduct =
                        result?.Product?.toString()?.trim()

                    String returnedPlant =
                        result?.Plant?.toString()?.trim()

                    returnedProduct == material &&
                    returnedPlant == plant
                }

                if (!materialPlantExists) {
                    materialPlantError =
                        "API returned data, but material ${material} was not found in plant ${plant}"
                }

            } else {

                materialPlantError =
                    "Material ${material} does not exist in plant ${plant}"
            }
        }

    } catch (Exception e) {

        materialPlantExists = false

        materialPlantError =
            "Cannot parse Material/Plant API response for material ${material}, plant ${plant}: ${e.message}"
    }

    message.setProperty(
        "MaterialPlantExists",
        materialPlantExists
    )

    message.setProperty(
        "MaterialPlantError",
        materialPlantError
    )

    return message
}
