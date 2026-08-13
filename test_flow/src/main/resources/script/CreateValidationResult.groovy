import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.MarkupBuilder

def Message processData(Message message) {

    String originalRecord =
        message.getProperty("OriginalRecord")?.toString() ?: ""

    boolean vendorExists =
        message.getProperty("VendorExists")?.toString()?.toBoolean() ?: false

    boolean materialPlantExists =
        message.getProperty("MaterialPlantExists")?.toString()?.toBoolean() ?: false

    // New validation from ValidateQuantityPrice.groovy
    boolean quantityPriceValid =
        message.getProperty("ValueValidationPassed")?.toString()?.toBoolean() ?: false


    String vendorError =
        message.getProperty("VendorError")?.toString() ?: ""

    String materialPlantError =
        message.getProperty("MaterialPlantError")?.toString() ?: ""

    String quantityPriceError =
        message.getProperty("ValueValidationErrors")?.toString() ?: ""


    // Combine all validation results
    boolean valid =
        vendorExists &&
        materialPlantExists &&
        quantityPriceValid


    StringWriter writer = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(writer)


    xml.ValidationResult {

        Valid(valid.toString())


        // Keep original split record
        if (originalRecord.trim()) {
            mkp.yieldUnescaped(originalRecord)
        }


        Errors {

            if (!vendorExists) {

                Error(
                    vendorError.trim()
                        ? vendorError
                        : "Vendor does not exist"
                )
            }


            if (!materialPlantExists) {

                Error(
                    materialPlantError.trim()
                        ? materialPlantError
                        : "Material does not exist in the specified plant"
                )
            }


            if (!quantityPriceValid) {

                Error(
                    quantityPriceError.trim()
                        ? quantityPriceError
                        : "Quantity or Price validation failed"
                )
            }

        }
    }


    message.setBody(writer.toString())

    return message
}