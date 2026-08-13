import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.MarkupBuilder

def Message processData(Message message) {

    // ------------------------------------------------------------
    // 1. Preserve the current split record
    // ------------------------------------------------------------
    String currentBody = message.getBody(String) ?: ""

    String originalRecord =
        message.getProperty("OriginalRecord")?.toString() ?: ""

    // Fallback: if the old PrepareValidation step no longer sets
    // OriginalRecord, use the current split <Item> body.
    if (!originalRecord.trim()) {
        originalRecord = currentBody
    }

    // ------------------------------------------------------------
    // 2. Read cache validation result
    // ------------------------------------------------------------
    boolean cacheValidationValid =
        message.getProperty("CacheValidationValid")
               ?.toString()
               ?.toBoolean() ?: false

    String cacheValidationErrors =
        message.getProperty("CacheValidationErrors")
               ?.toString() ?: ""

    // ------------------------------------------------------------
    // 3. Read local Quantity / Price validation result
    // ------------------------------------------------------------
    boolean valueValidationPassed =
        message.getProperty("ValueValidationPassed")
               ?.toString()
               ?.toBoolean() ?: false

    String valueValidationErrors =
        message.getProperty("ValueValidationErrors")
               ?.toString() ?: ""

    // ------------------------------------------------------------
    // 4. Overall row result
    // ------------------------------------------------------------
    boolean valid =
        cacheValidationValid && valueValidationPassed

    List<String> errors = []

    if (!cacheValidationValid) {
        if (cacheValidationErrors.trim()) {
            // CheckAllCaches joins errors with " | "
            cacheValidationErrors
                .split(/\s*\|\s*/)
                .findAll { it?.trim() }
                .each { errors << it.trim() }
        } else {
            errors << "Master-data cache validation failed."
        }
    }

    if (!valueValidationPassed) {
        if (valueValidationErrors.trim()) {
            // ValidateQuantityPrice also joins errors with " | "
            valueValidationErrors
                .split(/\s*\|\s*/)
                .findAll { it?.trim() }
                .each { errors << it.trim() }
        } else {
            errors << "Quantity/Price validation failed."
        }
    }

    // Optional convenience properties for Trace / Router
    message.setProperty("ValidationPassed", valid.toString())
    message.setProperty("ValidationErrors", errors.join(" | "))
    message.setProperty("ValidationErrorCount", errors.size().toString())

    // ------------------------------------------------------------
    // 5. Build ValidationResult XML
    // ------------------------------------------------------------
    StringWriter writer = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(writer)

    xml.ValidationResult {

        Valid(valid.toString())

        // Insert original split record unchanged
        if (originalRecord.trim()) {
            mkp.yieldUnescaped(originalRecord)
        }

        Errors {
            errors.each { err ->
                Error(err)
            }
        }
    }

    message.setBody(writer.toString())

    return message
}
