package com.vendorfraud.vendor.dto;

import com.vendorfraud.vendor.Vendor;

/** Vendor as exposed to clients. The entity itself never leaves the service layer. */
public record VendorResponse(Long id, String vendorCode, String name, String contactEmail, boolean active) {

    public static VendorResponse from(Vendor vendor) {
        return new VendorResponse(vendor.getId(), vendor.getVendorCode(), vendor.getName(),
                vendor.getContactEmail(), vendor.isActive());
    }
}
