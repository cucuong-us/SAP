import com.sap.gateway.ip.core.customdev.util.Message
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.util.XmlSlurper

def Message processData(Message message) {

    // ------------------------------------------------------------
    // 1. Read current split row XML
    // ------------------------------------------------------------
    def body = message.getBody(String)
    def xml = new XmlSlurper(false, false).parseText(body)

    def getField = { String name ->
        def node = xml."${name}"
        if (node && node.size() > 0) {
            return node.text()?.trim() ?: ""
        }

        // Fallback in case current body still contains <Item> wrapper
        def found = xml.depthFirst().find { it.name() == name }
        return found?.text()?.trim() ?: ""
    }

    def vendor                 = getField("Vendor")
    def material               = getField("Material")
    def plant                  = getField("Plant")
    def companyCode            = getField("CompanyCode")
    def purchasingOrganization = getField("PurchasingOrganization")
    def storageLocation        = getField("StorageLocation")
    def poUnit                 = getField("POUnit")
    def documentType           = getField("DocumentType")
    def valuationArea          = getField("ValuationArea")

    // In this PO flow ValuationArea normally corresponds to Plant.
    // If a ValuationArea field exists in the XML, use it; otherwise use Plant.
    if (!valuationArea) {
        valuationArea = plant
    }

    // ------------------------------------------------------------
    // 2. Parse caches from Exchange Properties
    // ------------------------------------------------------------
    def slurper = new JsonSlurper()

    def parseCache = { String propertyName ->
        def raw = message.getProperty(propertyName)
        if (raw == null || raw.toString().trim().isEmpty()) {
            return [:]
        }
        return slurper.parseText(raw.toString())
    }

    def materialPlantCache       = parseCache("MaterialPlantCache")
    def supplyPlanningCache      = parseCache("SupplyPlanningCache")
    def valuationCache           = parseCache("ValuationCache")
    def storageLocationCache     = parseCache("StorageLocationCache")
    def poUnitCache              = parseCache("POUnitCache")
    def supplierCompanyCache     = parseCache("SupplierCompanyCache")
    def supplierPurchOrgCache    = parseCache("SupplierPurchasingOrgCache")

    def errors = []

    // ------------------------------------------------------------
    // 3. Purchase Order Document Type
    //    This value was checked immediately before this script via
    //    RFC_READ_TABLE on T161 with BSTYP = 'F' and BSART = DocumentType.
    // ------------------------------------------------------------
    def documentTypeValid = message.getProperty("DocumentTypeValid")?.toString()?.equalsIgnoreCase("true") ?: false
    def existingDocumentTypeError = message.getProperty("DocumentTypeError")?.toString()?.trim() ?: ""

    if (documentTypeValid) {
        message.setProperty("DocumentTypeError", "")
    } else {
        def err = existingDocumentTypeError ?: (documentType ?
                "DocumentType ${documentType} is not configured as a Purchase Order document type in T161" :
                "DocumentType is required")
        message.setProperty("DocumentTypeError", err)
        errors << err
    }

    // ------------------------------------------------------------
    // 4. Supplier + Company Code
    //
    // IMPORTANT:
    // Keep this only as a WARNING / diagnostic check.
    // It does NOT make the PO row invalid.
    // ------------------------------------------------------------
    def supplierCompanyKey = "${vendor}|${companyCode}"
    def supplierCompanyValid = supplierCompanyCache[supplierCompanyKey] == true

    message.setProperty("SupplierCompanyExists", supplierCompanyValid.toString())

    if (supplierCompanyValid) {
        message.setProperty("SupplierCompanyWarning", "")
        message.setProperty("SupplierCompanyError", "")
    } else {
        def warning = "Vendor ${vendor} is not maintained for Company Code ${companyCode}"
        message.setProperty("SupplierCompanyWarning", warning)

        // Keep legacy property name for Trace compatibility,
        // but DO NOT add it to the hard-fail errors list.
        message.setProperty("SupplierCompanyError", warning)
    }

    // ------------------------------------------------------------
    // 5. Supplier + Purchasing Organization
    //
    // This remains a HARD FAIL because it is purchasing-relevant.
    // ------------------------------------------------------------
    def supplierPurchOrgKey = "${vendor}|${purchasingOrganization}"
    def supplierPurchOrgValid = supplierPurchOrgCache[supplierPurchOrgKey] == true

    message.setProperty("SupplierPurchOrgExists", supplierPurchOrgValid.toString())

    if (supplierPurchOrgValid) {
        message.setProperty("SupplierPurchOrgError", "")
    } else {
        def err = "Vendor ${vendor} is not maintained for Purchasing Organization ${purchasingOrganization}"
        message.setProperty("SupplierPurchOrgError", err)
        errors << err
    }

    // Vendor validity is now based ONLY on Supplier + Purchasing Organization.
    // Supplier + Company Code is diagnostic only.
    def vendorValid = supplierPurchOrgValid
    message.setProperty("VendorExists", vendorValid.toString())

    if (vendorValid) {
        message.setProperty("VendorError", "")
    } else {
        message.setProperty(
            "VendorError",
            "Vendor ${vendor} is not maintained for Purchasing Organization ${purchasingOrganization}"
        )
    }

    // ------------------------------------------------------------
    // 6. Material + Plant
    // ------------------------------------------------------------
    def materialPlantKey = "${material}|${plant}"
    def materialPlantValid = materialPlantCache[materialPlantKey] == true

    message.setProperty("MaterialPlantExists", materialPlantValid.toString())

    if (materialPlantValid) {
        message.setProperty("MaterialPlantError", "")
    } else {
        def err = "Material ${material} is not maintained in Plant ${plant}"
        message.setProperty("MaterialPlantError", err)
        errors << err
    }

    // ------------------------------------------------------------
    // 7. Supply Planning / Procurement Type
    //    F = external procurement
    //    X = both external + in-house
    // ------------------------------------------------------------
    def supplyPlanning = supplyPlanningCache[materialPlantKey]
    def procurementType = supplyPlanning?.procurementType?.toString()?.trim() ?: ""
    def supplyPlanningExists = (supplyPlanning != null)
    def externalProcurementValid = supplyPlanningExists && procurementType in ["F", "X"]

    message.setProperty("SupplyPlanningExists", supplyPlanningExists.toString())
    message.setProperty("ProcurementType", procurementType)
    message.setProperty("ExternalProcurementValid", externalProcurementValid.toString())

    if (externalProcurementValid) {
        message.setProperty("SupplyPlanningError", "")
    } else {
        def err
        if (!supplyPlanningExists) {
            err = "No supply planning data found for Material ${material} in Plant ${plant}"
        } else {
            err = "Material ${material} in Plant ${plant} is not valid for external procurement (ProcurementType=${procurementType})"
        }
        message.setProperty("SupplyPlanningError", err)
        errors << err
    }

    // ------------------------------------------------------------
    // 8. Valuation
    // ------------------------------------------------------------
    def valuationKey = "${material}|${valuationArea}"
    def valuation = valuationCache[valuationKey]
    def valuationExists = (valuation != null)

    def markedForDeletion = false
    if (valuationExists) {
        def rawDeletion = valuation?.isMarkedForDeletion
        markedForDeletion = (rawDeletion == true || rawDeletion?.toString()?.equalsIgnoreCase("true"))
    }

    def valuationValid = valuationExists && !markedForDeletion

    message.setProperty("ValuationExists", valuationExists.toString())
    message.setProperty("ValuationValid", valuationValid.toString())

    if (valuationExists) {
        message.setProperty("ValuationBaseUnit", valuation?.baseUnit?.toString()?.trim() ?: "")
        message.setProperty("ValuationCurrency", valuation?.currency?.toString()?.trim() ?: "")
        message.setProperty("ValuationPriceUnitQty", valuation?.priceUnitQty?.toString()?.trim() ?: "")
        message.setProperty("ValuationStandardPrice", valuation?.standardPrice?.toString()?.trim() ?: "")
        message.setProperty("ValuationMovingAveragePrice", valuation?.movingAveragePrice?.toString()?.trim() ?: "")
    } else {
        message.setProperty("ValuationBaseUnit", "")
        message.setProperty("ValuationCurrency", "")
        message.setProperty("ValuationPriceUnitQty", "")
        message.setProperty("ValuationStandardPrice", "")
        message.setProperty("ValuationMovingAveragePrice", "")
    }

    if (valuationValid) {
        message.setProperty("ValuationError", "")
    } else {
        def err = !valuationExists ?
                "No valuation data found for Material ${material} in Valuation Area ${valuationArea}" :
                "Material ${material} is marked for deletion in Valuation Area ${valuationArea}"
        message.setProperty("ValuationError", err)
        errors << err
    }

    // ------------------------------------------------------------
    // 9. Storage Location
    // ------------------------------------------------------------
    def storageLocationKey = "${material}|${plant}|${storageLocation}"
    def storageLocationValid = storageLocationCache[storageLocationKey] == true

    message.setProperty("StorageLocationExists", storageLocationValid.toString())

    if (storageLocationValid) {
        message.setProperty("StorageLocationError", "")
    } else {
        def err = "Material ${material} is not maintained in Storage Location ${storageLocation} of Plant ${plant}"
        message.setProperty("StorageLocationError", err)
        errors << err
    }

    // ------------------------------------------------------------
    // 10. PO Unit
    //    Pass when POUnit = BaseUnit OR is maintained as alternative unit
    // ------------------------------------------------------------
    def baseUnit = valuation?.baseUnit?.toString()?.trim() ?: ""
    def poUnitKey = "${material}|${poUnit}"

    def poUnitIsBaseUnit = poUnit && baseUnit && poUnit.equalsIgnoreCase(baseUnit)
    def poUnitIsAlternative = poUnitCache[poUnitKey] == true
    def poUnitValid = poUnitIsBaseUnit || poUnitIsAlternative

    message.setProperty("POUnitValid", poUnitValid.toString())
    message.setProperty("POUnitIsBaseUnit", poUnitIsBaseUnit.toString())
    message.setProperty("POUnitIsAlternative", poUnitIsAlternative.toString())

    if (poUnitValid) {
        message.setProperty("POUnitError", "")
    } else {
        def err = "PO Unit ${poUnit} is not valid for Material ${material}"
        message.setProperty("POUnitError", err)
        errors << err
    }

    // ------------------------------------------------------------
    // 11. Overall cache validation result
    // ------------------------------------------------------------
    def cacheValidationValid = errors.isEmpty()

    message.setProperty("CacheValidationValid", cacheValidationValid.toString())
    message.setProperty("CacheValidationErrors", errors.join(" | "))
    message.setProperty("CacheValidationErrorCount", errors.size().toString())

    // Optional JSON summary, useful in Trace
    def summary = [
        vendor                  : vendor,
        material                : material,
        plant                   : plant,
        companyCode             : companyCode,
        purchasingOrganization  : purchasingOrganization,
        storageLocation         : storageLocation,
        poUnit                   : poUnit,
        documentType             : documentType,
        documentTypeValid        : documentTypeValid,
        valuationArea           : valuationArea,
        supplierCompanyValid    : supplierCompanyValid,
        supplierCompanyHardFail : false,
        supplierCompanyWarning  : message.getProperty("SupplierCompanyWarning")?.toString() ?: "",
        supplierPurchOrgValid   : supplierPurchOrgValid,
        materialPlantValid      : materialPlantValid,
        procurementType         : procurementType,
        externalProcurementValid: externalProcurementValid,
        valuationValid          : valuationValid,
        storageLocationValid    : storageLocationValid,
        poUnitValid             : poUnitValid,
        overallValid            : cacheValidationValid
    ]

    message.setProperty("CacheValidationSummary", JsonOutput.toJson(summary))

    // Do NOT change the body. Downstream steps continue using the split row XML.
    return message
}
