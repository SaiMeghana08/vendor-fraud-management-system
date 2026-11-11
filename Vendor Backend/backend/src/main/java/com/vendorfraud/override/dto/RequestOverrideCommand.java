package com.vendorfraud.override.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestOverrideCommand(
        @NotNull(message = "A bank-change request must be identified") Long bankChangeRequestId,
        @NotBlank(message = "A justification is required")
        @Size(min = 20, max = 1000,
              message = "An emergency override needs a substantive justification (at least 20 characters)")
        String justification,
        @NotBlank(message = "An evidence reference is required") @Size(max = 120)
        String evidenceReference) {
}
