import com.sap.gateway.ip.core.customdev.util.Message
import java.math.BigDecimal

Message processData(Message message) {

    String body = message.getBody(String)
    def xml = new XmlSlurper(false, false).parseText(body)

    List<String> errors = []

    def items = xml.'**'.findAll { node ->
        node.name() == 'Item'
    }

    if (items.isEmpty()) {
        errors.add("No purchase order items found in the payload.")
    }

    items.eachWithIndex { item, index ->

        int itemNumber = index + 1

        String quantityText = item.Quantity.text()?.trim()
        String priceText = item.Price.text()?.trim()

        // Quantity validation
        if (!quantityText) {
            errors.add("Item ${itemNumber}: Quantity is missing.")
        } else {
            try {
                BigDecimal quantity = new BigDecimal(quantityText)

                if (quantity <= BigDecimal.ZERO) {
                    errors.add(
                        "Item ${itemNumber}: Quantity must be greater than 0. Current value: ${quantityText}"
                    )
                }

            } catch (NumberFormatException e) {
                errors.add(
                    "Item ${itemNumber}: Quantity is not a valid number. Current value: ${quantityText}"
                )
            }
        }

        // Price validation
        if (!priceText) {
            errors.add("Item ${itemNumber}: Price is missing.")
        } else {
            try {
                BigDecimal price = new BigDecimal(priceText)

                if (price <= BigDecimal.ZERO) {
                    errors.add(
                        "Item ${itemNumber}: Price must be greater than 0. Current value: ${priceText}"
                    )
                }

            } catch (NumberFormatException e) {
                errors.add(
                    "Item ${itemNumber}: Price is not a valid number. Current value: ${priceText}"
                )
            }
        }
    }

    if (errors.isEmpty()) {

        message.setProperty("ValueValidationPassed", "true")
        message.setProperty("ValueValidationErrors", "")

    } else {

        message.setProperty("ValueValidationPassed", "false")
        message.setProperty(
            "ValueValidationErrors",
            errors.join(" | ")
        )
    }

    return message
}
