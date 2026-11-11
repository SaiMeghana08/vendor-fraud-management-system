package com.vendorfraud.bankchange.dto;

import com.vendorfraud.bankchange.BankChangeRequest;
import com.vendorfraud.bankchange.BankChangeStatus;
import com.vendorfraud.common.MaskingUtils;
import java.time.Instant;
import java.util.List;

/**
 * A bank-change request as exposed to clients. Both the current and the
 * proposed account references are masked.
 */
public record BankChangeRequestResponse(
        Long id,
        String requestReference,
        Long vendorId,
        String vendorName,
        String vendorCode,
        Long makerId,
        String makerName,
        String currentMaskedAccountReference,
        String proposedAccountHolderName,
        String proposedBankName,
        String proposedMaskedAccountReference,
        String proposedRoutingCode,
        String reason,
        String evidenceReference,
        BankChangeStatus status,
        String terminalReason,
        CoolingOffView coolingOff,
        Instant activatedAt,
        Instant createdAt,
        Instant updatedAt,
        List<ApprovalTaskResponse> approvalTasks) {

    public static BankChangeRequestResponse from(BankChangeRequest request,
                                                 List<ApprovalTaskResponse> tasks,
                                                 CoolingOffView coolingOff) {
        return new BankChangeRequestResponse(
                request.getId(),
                request.getRequestReference(),
                request.getVendor().getId(),
                request.getVendor().getName(),
                request.getVendor().getVendorCode(),
                request.getMaker().getId(),
                request.getMaker().getFullName(),
                request.getCurrentBankAccount() == null ? null
                        : MaskingUtils.maskAccountReference(
                                request.getCurrentBankAccount().getAccountReference()),
                request.getProposedAccountHolderName(),
                request.getProposedBankName(),
                MaskingUtils.maskAccountReference(request.getProposedAccountReference()),
                request.getProposedRoutingCode(),
                request.getReason(),
                request.getEvidenceReference(),
                request.getStatus(),
                request.getTerminalReason(),
                coolingOff,
                request.getActivatedAt(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                tasks);
    }
}
