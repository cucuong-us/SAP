import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.MarkupBuilder

def Message processData(Message message) {

    // ------------------------------------------------------------
    // 1. Keep the current split row XML
    // ------------------------------------------------------------
    String currentRow = message.getBody(String) ?: ""

    // ------------------------------------------------------------
    // 2. Read RowNumber
    // ------------------------------------------------------------
    String rowNumber =
        message.getProperty("RowNumber")?.toString() ?: ""

    // ------------------------------------------------------------
    // 3. Build one combined validation error message
    // ------------------------------------------------------------
    String errorMessage =
        message.getProperty("ValidationErrors")?.toString() ?: ""

    // Fallback if ValidationErrors was not created
    if (!errorMessage.trim()) {

        String cacheErrors =
            message.getProperty("CacheValidationErrors")?.toString() ?: ""

        String valueErrors =
            message.getProperty("ValueValidationErrors")?.toString() ?: ""

        errorMessage = [cacheErrors, valueErrors]
            .findAll { it?.trim() }
            .join(" | ")
    }

    // Last fallback so the error row is never blank
    if (!errorMessage.trim()) {
        errorMessage = "Validation failed"
    }

    // ------------------------------------------------------------
    // 4. Build normalized failed-row XML
    // ------------------------------------------------------------
    StringWriter writer = new StringWriter()
    MarkupBuilder xml = new MarkupBuilder(writer)

    xml.RowResult {

        RowNumber(rowNumber)

        Data {
            // Keep the original split row unchanged
            mkp.yieldUnescaped(currentRow)
        }

        Status("ERROR")
        ErrorStage("VALIDATION")
        ErrorMessage(errorMessage)
        IDocNumber("")
    }

    // ------------------------------------------------------------
    // 5. Replace body with RowResult
    // ------------------------------------------------------------
    message.setBody(writer.toString())

    // ------------------------------------------------------------
    // 6. Helpful properties for Trace / downstream routing
    // ------------------------------------------------------------
    message.setProperty("Status", "ERROR")
    message.setProperty("ErrorStage", "VALIDATION")
    message.setProperty("ErrorMessage", errorMessage)
    message.setProperty("IDocNumber", "")

    return message
}
