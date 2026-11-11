package com.vendorfraud.vendor.dto;

import com.vendorfraud.common.MaskingUtils;
import com.vendorfraud.vendor.BankAccountStatus;
import com.vendorfraud.vendor.VendorBankAccount;
import java.time.Instant;

/**
 * Banking record as exposed to clients. The account reference is always
 * masked; the unmasked value never leaves the service layer.
 */
public record VendorBankAccountResponse(
        Long id,
        String accountHolderName,
        String bankName,
        String maskedAccountReference,
        String routingCode,
        BankAccountStatus status,
        Instant activatedAt,
        Instant supersededAt) {

    public static VendorBankAccountResponse from(VendorBankAccount account) {
        if (account == null) {
            return null;
        }
        return new VendorBankAccountResponse(account.getId(), account.getAccountHolderName(),
                account.getBankName(), MaskingUtils.maskAccountReference(account.getAccountReference()),
                account.getRoutingCode(), account.getStatus(), account.getActivatedAt(),
                account.getSupersededAt());
    }
}
