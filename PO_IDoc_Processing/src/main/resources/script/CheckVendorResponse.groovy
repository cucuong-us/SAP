import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import java.util.Locale

def Message processData(Message message) {

    String vendor =
        message.getProperty("Vendor")?.toString()?.trim() ?: ""

    boolean vendorExists = false
    String vendorError = ""

    try {

        /*
         * Read the JSON response as a stream.
         * This avoids the JSONSlurper "without streaming" incompatibility.
         */
        java.io.Reader reader =
            message.getBody(java.io.Reader.class)

        if (reader == null) {

            vendorError =
                "Vendor API returned an empty response for vendor ${vendor}"

        } else {

            def json = new JsonSlurper().parse(reader)

            /*
             * Supports:
             * 1. OData collection response: d.results[]
             * 2. OData single-record response: d.Supplier
             */
            def records = []

            if (json?.d?.results instanceof Collection) {
                records = json.d.results
            } else if (json?.d?.Supplier != null) {
                records = [json.d]
            }

            if (records && !records.isEmpty()) {

                String normalizedInputVendor =
                    normalizeVendor(vendor)

                vendorExists = records.any { record ->

                    String returnedSupplier =
                        record?.Supplier?.toString()?.trim() ?: ""

                    String normalizedReturnedSupplier =
                        normalizeVendor(returnedSupplier)

                    normalizedReturnedSupplier ==
                        normalizedInputVendor
                }

                if (!vendorExists) {

                    List<String> returnedSuppliers =
                        records.collect { record ->
                            record?.Supplier?.toString()?.trim()
                        }.findAll { supplier ->
                            supplier != null && !supplier.isEmpty()
                        }

                    vendorError =
                        "Vendor ${vendor} was not found. " +
                        "Vendor API returned suppliers: " +
                        returnedSuppliers.join(", ")
                }

            } else {

                vendorError =
                    "Vendor API returned no supplier records for vendor ${vendor}"
            }
        }

    } catch (Exception e) {

        vendorExists = false

        vendorError =
            "Cannot parse Vendor API response for vendor ${vendor}: " +
            "${e.message}"
    }

    message.setProperty("VendorExists", vendorExists)
    message.setProperty("VendorError", vendorError)

    return message
}


/*
 * Normalize numeric supplier IDs into SAP's
 * 10-character technical format.
 *
 * 17300001   -> 0017300001
 * 0017300001 -> 0017300001
 *
 * Alphanumeric suppliers such as TESTVGU remain unchanged.
 */
String normalizeVendor(Object value) {

    String supplier =
        value?.toString()
             ?.trim()
             ?.toUpperCase(Locale.ROOT) ?: ""

    if (supplier ==~ /\d+/ && supplier.length() < 10) {
        return supplier.padLeft(10, '0')
    }

    return supplier
}