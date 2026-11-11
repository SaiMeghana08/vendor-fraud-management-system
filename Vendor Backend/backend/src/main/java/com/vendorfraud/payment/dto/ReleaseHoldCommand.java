package com.vendorfraud.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Releasing a hold always requires a justification. */
public record ReleaseHoldCommand(
        @NotBlank(message = "A release justification is mandatory")
        @Size(min = 10, max = 1000,
              message = "Please give a meaningful justification (at least 10 characters)")
        String justification) {
}
