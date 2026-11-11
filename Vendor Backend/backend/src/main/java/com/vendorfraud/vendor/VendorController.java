package com.vendorfraud.vendor;

import com.vendorfraud.vendor.dto.VendorBankAccountResponse;
import com.vendorfraud.vendor.dto.VendorResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendors")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    /** Feeds the searchable active-vendor dropdown. Inactive vendors never appear. */
    @GetMapping
    @PreAuthorize("hasAnyRole('MAKER','APPROVER','ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<List<VendorResponse>> listActive(
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(vendorService.searchActiveVendors(search));
    }

    @GetMapping("/{vendorId}")
    @PreAuthorize("hasAnyRole('MAKER','APPROVER','ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<VendorResponse> get(@PathVariable Long vendorId) {
        return ResponseEntity.ok(vendorService.getActiveVendor(vendorId));
    }

    /** The masked, read-only current bank reference on the Maker form. */
    @GetMapping("/{vendorId}/current-bank-account")
    @PreAuthorize("hasAnyRole('MAKER','APPROVER','ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<VendorBankAccountResponse> currentBankAccount(@PathVariable Long vendorId) {
        return ResponseEntity.ok(vendorService.getCurrentBankAccount(vendorId));
    }

    @GetMapping("/{vendorId}/bank-account-history")
    @PreAuthorize("hasAnyRole('APPROVER','ACCOUNTS_PAYABLE','ADMINISTRATOR')")
    public ResponseEntity<List<VendorBankAccountResponse>> history(@PathVariable Long vendorId) {
        return ResponseEntity.ok(vendorService.getBankAccountHistory(vendorId));
    }
}
