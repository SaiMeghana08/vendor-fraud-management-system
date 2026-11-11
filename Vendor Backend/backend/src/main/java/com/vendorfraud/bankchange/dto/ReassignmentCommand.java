package com.vendorfraud.bankchange.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReassignmentCommand(
        @NotNull(message = "A replacement approver must be selected") Long replacementUserId,
        @NotBlank(message = "A reassignment reason is mandatory")
        @Size(min = 10, max = 1000, message = "Please give a meaningful reason (at least 10 characters)")
        String reason) {
}
