package com.vendorfraud.payment.dto;

/**
 * The answer to "may this payment be processed right now", recomputed
 * from live state on every call rather than read from a cached flag.
 */
public record PaymentEligibilityResponse(
        Long paymentId,
        String paymentReference,
        boolean eligible,
        String reason,
        long activeHoldCount) {
}
