package com.vendorfraud.bankchange;

public enum ApprovalTaskStatus {
    PENDING,
    APPROVED,
    REJECTED,
    /** Superseded by a reassignment; kept so the trail stays complete. */
    REASSIGNED
}
