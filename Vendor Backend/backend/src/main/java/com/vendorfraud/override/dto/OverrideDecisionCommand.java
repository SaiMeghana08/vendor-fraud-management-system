package com.vendorfraud.override.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OverrideDecisionCommand(
        @NotNull(message = "A decision is required") Boolean approved,
        @Size(max = 1000) String comment) {
}
