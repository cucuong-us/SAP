import com.sap.gateway.ip.core.customdev.util.Message
import groovy.util.XmlSlurper

def Message processData(Message message) {

    // ------------------------------------------------------------
    // 1. Read current split row XML
    // ------------------------------------------------------------
    String body = message.getBody(String) ?: ""
    def xml = new XmlSlurper(false, false).parseText(body)

    def getField = { String name ->
        def node = xml."${name}"
        if (node && node.size() > 0) {
            return node.text()?.trim() ?: ""
        }

        def found = xml.depthFirst().find { it.name() == name }
        return found?.text()?.trim() ?: ""
    }

    String quantityText  = getField("Quantity")
    String priceText     = getField("Price")
    String priceUnitText = getField("PriceUnit")

    List<String> errors = []

    // ------------------------------------------------------------
    // 2. Validate Quantity
    // ------------------------------------------------------------
    BigDecimal quantity = null

    if (!quantityText) {
        errors << "Quantity is required"
    } else {
        try {
            quantity = new BigDecimal(quantityText)
            if (quantity <= 0) {
                errors << "Quantity must be greater than 0"
            }
        } catch (Exception e) {
            errors << "Quantity must be a valid number"
        }
    }

    // ------------------------------------------------------------
    // 3. Validate Price
    // ------------------------------------------------------------
    BigDecimal price = null

    if (!priceText) {
        errors << "Price is required"
    } else {
        try {
            price = new BigDecimal(priceText)
            if (price <= 0) {
                errors << "Price must be greater than 0"
            }
        } catch (Exception e) {
            errors << "Price must be a valid number"
        }
    }

    // ------------------------------------------------------------
    // 4. Validate PriceUnit
    // ------------------------------------------------------------
    BigDecimal priceUnit = null

    if (!priceUnitText) {
        errors << "PriceUnit is required"
    } else {
        try {
            priceUnit = new BigDecimal(priceUnitText)
            if (priceUnit <= 0) {
                errors << "PriceUnit must be greater than 0"
            }
        } catch (Exception e) {
            errors << "PriceUnit must be a valid number"
        }
    }

    // ------------------------------------------------------------
    // 5. Set validation properties
    // ------------------------------------------------------------
    boolean passed = errors.isEmpty()

    message.setProperty("ValueValidationPassed", passed.toString())
    message.setProperty("ValueValidationErrors", errors.join(" | "))
    message.setProperty("ValueValidationErrorCount", errors.size().toString())

    // Individual flags for easier Trace/debugging
    message.setProperty(
        "QuantityValid",
        (quantity != null && quantity > 0).toString()
    )

    message.setProperty(
        "PriceValid",
        (price != null && price > 0).toString()
    )

    message.setProperty(
        "PriceUnitValid",
        (priceUnit != null && priceUnit > 0).toString()
    )

    // Preserve parsed values for downstream use/debugging
    message.setProperty("ValidatedQuantity", quantity != null ? quantity.toPlainString() : "")
    message.setProperty("ValidatedPrice", price != null ? price.toPlainString() : "")
    message.setProperty("ValidatedPriceUnit", priceUnit != null ? priceUnit.toPlainString() : "")

    // Do not change the body
    return message
}
