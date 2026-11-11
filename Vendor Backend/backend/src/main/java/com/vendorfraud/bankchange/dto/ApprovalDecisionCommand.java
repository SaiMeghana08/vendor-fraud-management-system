package com.vendorfraud.bankchange.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * An approve or reject decision. The stage is never supplied by the
 * client - it comes from the task being decided.
 *
 * @param approved true to approve, false to reject
 * @param comment  mandatory and meaningful when rejecting
 */
public record ApprovalDecisionCommand(
        @NotNull(message = "A decision is required") Boolean approved,
        @Size(max = 1000) String comment) {
}
