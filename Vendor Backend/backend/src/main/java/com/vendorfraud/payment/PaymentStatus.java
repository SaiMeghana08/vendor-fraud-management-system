package com.vendorfraud.payment;

public enum PaymentStatus {
    /** Created and payable, subject to eligibility checks. */
    CREATED,
    /** At least one active hold applies. */
    ON_HOLD,
    PROCESSED,
    CANCELLED;

    public boolean isTerminal() {
        return this == PROCESSED || this == CANCELLED;
    }
}
