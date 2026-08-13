import com.sap.gateway.ip.core.customdev.util.Message
import java.net.URLDecoder

def Message processData(Message message) {

    // =====================================================
    // 1. Save original Excel file payload
    // =====================================================

    byte[] originalFile = message.getBody(byte[])

    message.setProperty(
        "OriginalExcelFile",
        originalFile
    )


    // =====================================================
    // 2. Extract original filename from GCS GetObjectURL
    // =====================================================

    String objectURL = message.getProperty("GetObjectURL")?.toString()

    if (objectURL != null && !objectURL.isEmpty()) {

        // Decode URL:
        // order%2FPurchaseOrder_Batch_04.xlsx
        // becomes:
        // order/PurchaseOrder_Batch_04.xlsx

        objectURL = URLDecoder.decode(objectURL, "UTF-8")


        // Remove query parameters:
        // PurchaseOrder_Batch_04.xlsx?generation=xxx&alt=media
        //
        // becomes:
        // PurchaseOrder_Batch_04.xlsx

        objectURL = objectURL.split("\\?")[0]


        // Extract filename after last "/"

        String fileName = objectURL.substring(
            objectURL.lastIndexOf("/") + 1
        )


        // Save filename for later use

        message.setProperty(
            "OriginalFileName",
            fileName
        )

    } else {

        // Fallback if GetObjectURL is missing

        message.setProperty(
            "OriginalFileName",
            "Unknown.xlsx"
        )
    }


    return message
}