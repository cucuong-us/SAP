import com.sap.gateway.ip.core.customdev.util.Message

def Message processData(Message message) {

    // Restore original XLSX payload saved before Excel conversion
    byte[] originalFile = message.getProperty("OriginalExcelFile")

    if (originalFile != null) {
        message.setBody(originalFile)
    } else {
        throw new Exception("Original Excel file not found in exchange property: OriginalExcelFile")
    }

    return message
}
