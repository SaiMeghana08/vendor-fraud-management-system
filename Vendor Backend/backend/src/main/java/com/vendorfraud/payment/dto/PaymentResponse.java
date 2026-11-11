package com.vendorfraud.payment.dto;

import com.vendorfraud.common.MaskingUtils;
import com.vendorfraud.payment.Payment;
import com.vendorfraud.payment.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PaymentResponse(
        Long id,
        String paymentReference,
        Long vendorId,
        String vendorName,
        String maskedPayToAccountReference,
        BigDecimal amount,
        String currency,
        LocalDate dueDate,
        String description,
        PaymentStatus status,
        boolean eligibleForProcessing,
        String ineligibilityReason,
        long activeHoldCount,
        List<PaymentHoldResponse> holds,
        Instant processedAt,
        Instant createdAt) {

    public static PaymentResponse from(Payment payment, List<PaymentHoldResponse> holds,
                                       boolean eligible, String ineligibilityReason,
                                       long activeHoldCount) {
        return new PaymentResponse(payment.getId(), payment.getPaymentReference(),
                payment.getVendor().getId(), payment.getVendor().getName(),
                payment.getVendorBankAccount() == null ? null
                        : MaskingUtils.maskAccountReference(
                                payment.getVendorBankAccount().getAccountReference()),
                payment.getAmount(), payment.getCurrency(), payment.getDueDate(),
                payment.getDescription(), payment.getStatus(), eligible, ineligibilityReason,
                activeHoldCount, holds, payment.getProcessedAt(), payment.getCreatedAt());
    }
}
