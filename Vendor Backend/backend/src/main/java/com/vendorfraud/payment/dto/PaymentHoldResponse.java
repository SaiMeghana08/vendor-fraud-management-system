package com.vendorfraud.payment.dto;

import com.vendorfraud.payment.HoldStatus;
import com.vendorfraud.payment.PaymentHold;
import java.time.Instant;

public record PaymentHoldResponse(
        Long id,
        Long paymentId,
        Long bankChangeRequestId,
        String reason,
        HoldStatus status,
        String releaseJustification,
        Long releasedBy,
        Instant releasedAt,
        Instant createdAt) {

    public static PaymentHoldResponse from(PaymentHold hold) {
        return new PaymentHoldResponse(hold.getId(), hold.getPaymentId(),
                hold.getBankChangeRequestId(), hold.getReason(), hold.getStatus(),
                hold.getReleaseJustification(), hold.getReleasedBy(), hold.getReleasedAt(),
                hold.getCreatedAt());
    }
}
