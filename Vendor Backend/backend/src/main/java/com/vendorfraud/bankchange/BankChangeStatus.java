package com.vendorfraud.bankchange;

import java.util.EnumSet;
import java.util.Set;

/**
 * Explicit workflow states and the only transitions the service layer
 * will perform. Anything not listed here is rejected as out of order.
 */
public enum BankChangeStatus {

    PENDING_FIRST_APPROVAL,
    PENDING_SECOND_APPROVAL,
    COOLING_OFF,
    ACTIVATED,
    REJECTED,
    CANCELLED,
    /** Revalidation after cooling-off failed; needs an exception decision. */
    ACTIVATION_BLOCKED;

    public boolean isTerminal() {
        return this == ACTIVATED || this == REJECTED || this == CANCELLED;
    }

    /** Open means the request still occupies the one-per-vendor slot. */
    public boolean isOpen() {
        return !isTerminal();
    }

    public Set<BankChangeStatus> allowedNext() {
        return switch (this) {
            case PENDING_FIRST_APPROVAL ->
                    EnumSet.of(PENDING_SECOND_APPROVAL, REJECTED, CANCELLED);
            case PENDING_SECOND_APPROVAL ->
                    EnumSet.of(COOLING_OFF, REJECTED, CANCELLED);
            case COOLING_OFF ->
                    EnumSet.of(ACTIVATED, ACTIVATION_BLOCKED, CANCELLED);
            case ACTIVATION_BLOCKED ->
                    EnumSet.of(ACTIVATED, CANCELLED);
            case ACTIVATED, REJECTED, CANCELLED -> EnumSet.noneOf(BankChangeStatus.class);
        };
    }

    public boolean canTransitionTo(BankChangeStatus target) {
        return allowedNext().contains(target);
    }
}
