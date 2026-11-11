package com.vendorfraud.vendor;

public enum BankAccountStatus {
    /** The record payments are made against today. */
    ACTIVE,
    /** Replaced by a later record; kept for history, never deleted. */
    SUPERSEDED
}
