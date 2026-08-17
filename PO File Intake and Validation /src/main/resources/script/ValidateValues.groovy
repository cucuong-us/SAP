import com.sap.gateway.ip.core.customdev.util.Message

/**
 * Validate converted Purchase Order XML.
 *
 * Expected XML structure:
 *
 * <PurchaseOrders>
 *     <Item>
 *         <Vendor>100100</Vendor>
 *         <Material>MAT100</Material>
 *         <Plant>1100</Plant>
 *         <Quantity>10</Quantity>
 *         <Price>25</Price>
 *     </Item>
 * </PurchaseOrders>
 *
 * Checks:
 * - Vendor is not empty
 * - Material is not empty
 * - Plant is not empty
 * - Quantity is numeric and greater than 0
 * - Price is numeric and greater than 0
 *
 * If any row is invalid, the script throws an exception.
 * This causes the CPI message to fail and can later be handled
 * by an Exception Subprocess.
 */
Message processData(Message message) {

    String body = message.getBody(String)

    if (body == null || body.trim().isEmpty()) {
        throw new IllegalArgumentException(
            "Validation failed: Converted XML payload is empty."
        )
    }

    def xml

    try {
        xml = new XmlSlurper(false, false).parseText(body)
    } catch (Exception e) {
        throw new IllegalArgumentException(
            "Validation failed: Payload is not valid XML. ${e.message}"
        )
    }

    def items = xml.Item
    List<String> errors = []

    if (items == null || items.size() == 0) {
        throw new IllegalArgumentException(
            "Validation failed: No Item records were found in the XML."
        )
    }

    items.eachWithIndex { item, index ->

        // Excel row 1 is normally the header, so data starts at row 2.
        int excelRow = index + 2

        String vendor = item.Vendor.text()?.trim()
        String material = item.Material.text()?.trim()
        String plant = item.Plant.text()?.trim()
        String quantityText = item.Quantity.text()?.trim()
        String priceText = item.Price.text()?.trim()

        // Mandatory field checks
        if (!vendor) {
            errors.add("Row ${excelRow}: Vendor is empty")
        }

        if (!material) {
            errors.add("Row ${excelRow}: Material is empty")
        }

        if (!plant) {
            errors.add("Row ${excelRow}: Plant is empty")
        }

        // Quantity check
        if (!quantityText) {
            errors.add("Row ${excelRow}: Quantity is empty")
        } else {
            try {
                BigDecimal quantity = new BigDecimal(quantityText)

                if (quantity <= BigDecimal.ZERO) {
                    errors.add(
                        "Row ${excelRow}: Quantity must be greater than 0; received '${quantityText}'"
                    )
                }
            } catch (NumberFormatException ignored) {
                errors.add(
                    "Row ${excelRow}: Quantity must be numeric; received '${quantityText}'"
                )
            }
        }

        // Price check
        if (!priceText) {
            errors.add("Row ${excelRow}: Price is empty")
        } else {
            try {
                BigDecimal price = new BigDecimal(priceText)

                if (price <= BigDecimal.ZERO) {
                    errors.add(
                        "Row ${excelRow}: Price must be greater than 0; received '${priceText}'"
                    )
                }
            } catch (NumberFormatException ignored) {
                errors.add(
                    "Row ${excelRow}: Price must be numeric; received '${priceText}'"
                )
            }
        }
    }

    if (!errors.isEmpty()) {

        String validationMessage =
            "Purchase order validation failed. " +
            "${errors.size()} error(s) found: " +
            errors.join(" | ")

        message.setProperty("ValidationStatus", "INVALID")
        message.setProperty("ValidationErrorCount", errors.size().toString())
        message.setProperty("ValidationErrors", errors.join(" | "))

        throw new IllegalArgumentException(validationMessage)
    }

    message.setProperty("ValidationStatus", "VALID")
    message.setProperty("ValidationErrorCount", "0")
    message.setProperty("ValidationErrors", "")

    return message
}
