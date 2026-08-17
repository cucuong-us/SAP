import com.sap.gateway.ip.core.customdev.util.Message
import groovy.xml.XmlUtil

def Message processData(Message message) {

    String body = message.getBody(String)

    if (!body?.trim()) {
        return message
    }

    def xml = new XmlSlurper(false, false).parseText(body)

    // Supports both:
    // <PurchaseOrders><Item><Vendor>...</Vendor></Item></PurchaseOrders>
    // and
    // <Item><Vendor>...</Vendor></Item>
    def vendorNode = xml.Item.Vendor

    if (vendorNode.size() == 0) {
        vendorNode = xml.Vendor
    }

    String vendor = vendorNode.text()?.trim()

    // Only pad numeric vendor IDs.
    // Example:
    // 17300001   -> 0017300001
    // 10300015   -> 0010300015
    // 0017300001 -> 0017300001
    if (vendor && vendor ==~ /\d+/) {
        String formattedVendor = vendor.padLeft(10, '0')
        vendorNode.replaceBody(formattedVendor)
        message.setProperty("FormattedVendor", formattedVendor)
    } else {
        message.setProperty("FormattedVendor", vendor ?: "")
    }

    message.setBody(XmlUtil.serialize(xml))
    return message
}
