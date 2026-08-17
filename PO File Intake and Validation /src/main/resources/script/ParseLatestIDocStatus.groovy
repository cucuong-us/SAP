import com.sap.gateway.ip.core.customdev.util.Message

Message processData(Message message) {

    String body = message.getBody(String) ?: ""

    if (!body.trim()) {
        throw new IllegalArgumentException(
            "RFC_READ_TABLE response payload is empty."
        )
    }

    def xml

    try {
        xml = new XmlSlurper(false, false).parseText(body)
    } catch (Exception e) {
        throw new IllegalArgumentException(
            "RFC_READ_TABLE response is not valid XML: ${e.message}",
            e
        )
    }

    List<Map<String, Object>> statusRecords = []

    xml.'**'.findAll { node ->
        node.name() == "WA"
    }.each { node ->

        String wa = node.text() ?: ""

        if (wa.trim()) {

            String[] values = wa.split(/\|/, -1)

            if (values.length >= 2) {

                String counterText = values[0].trim()
                String statusText  = values[1].trim()
                String messageText = values.length > 2
                    ? values[2].trim()
                    : ""

                String parameter2 = values.length > 3
                    ? values[3].trim()
                    : ""

                long counterValue = 0L

                try {
                    counterValue = counterText.toLong()
                } catch (Exception ignored) {
                    counterValue = 0L
                }

                statusRecords << [
                    counter: counterValue,
                    status : statusText,
                    text   : messageText,
                    po     : parameter2
                ]
            }
        }
    }

    /*
     * No EDIDS status record found yet.
     * Continue polling.
     */
    if (statusRecords.isEmpty()) {

        message.setProperty("FinalStatus", "PROCESSING")
        message.setProperty("LatestStatusCounter", "")
        message.setProperty("StatusRecordCount", "0")
        message.setProperty("IDocStatusText", "")
        message.setProperty("PONumber", "")
        message.setProperty("RowStatus", "PROCESSING")
        message.setProperty("PollComplete", "false")

        return message
    }

    /*
     * Sort all records by EDIDS counter.
     */
    statusRecords = statusRecords.sort { record ->
        record.counter as Long
    }

    /*
     * Latest status record.
     */
    Map<String, Object> latest = statusRecords.last()

    String finalStatus = (latest.status ?: "")
        .toString()
        .trim()

    String latestStatusText = (latest.text ?: "")
        .toString()
        .trim()

    /*
     * Find the latest successful status 53 record
     * that contains a purchase order number in STAPA2.
     */
    List<Map<String, Object>> successRecordsWithPO =
        statusRecords.findAll { record ->

            record.status?.toString()?.trim() == "53" &&
            record.po?.toString()?.trim()
        }

    Map<String, Object> latestWithPO =
        successRecordsWithPO
            ? successRecordsWithPO.last()
            : null

    String poNumber = latestWithPO
        ? latestWithPO.po.toString().trim()
        : ""

    /*
     * Collect every status 51 message.
     * Keep their original EDIDS order and remove duplicates.
     */
    List<String> errorMessages = statusRecords
        .findAll { record ->
            record.status?.toString()?.trim() == "51" &&
            record.text?.toString()?.trim()
        }
        .collect { record ->
            record.text.toString().trim()
        }
        .unique()

    String allErrorTexts = errorMessages.join(" | ")

    /*
     * For failed IDocs, expose all error messages.
     * For other statuses, expose only the latest status text.
     */
    String statusTextForOutput =
        finalStatus == "51"
            ? allErrorTexts
            : latestStatusText

    message.setProperty(
        "FinalStatus",
        finalStatus
    )

    message.setProperty(
        "LatestStatusCounter",
        latest.counter.toString()
    )

    message.setProperty(
        "StatusRecordCount",
        statusRecords.size().toString()
    )

    message.setProperty(
        "IDocStatusText",
        statusTextForOutput
    )

    message.setProperty(
        "PONumber",
        poNumber
    )

    String pollComplete = "false"

    if (finalStatus == "53" && poNumber) {

        message.setProperty(
            "RowStatus",
            "POSTED"
        )

        pollComplete = "true"

    } else if (finalStatus == "51") {

        message.setProperty(
            "RowStatus",
            "FAILED"
        )

        pollComplete = "true"

    } else {

        message.setProperty(
            "RowStatus",
            "PROCESSING"
        )
    }

    message.setProperty(
        "PollComplete",
        pollComplete
    )

    return message
}