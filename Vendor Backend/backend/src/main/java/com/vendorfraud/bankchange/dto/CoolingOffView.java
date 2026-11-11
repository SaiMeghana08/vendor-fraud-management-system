package com.vendorfraud.bankchange.dto;

import java.time.Instant;

/**
 * Read-only cooling-off status for the UI countdown. The frontend
 * displays this; it has no control that can shorten or skip it.
 */
public record CoolingOffView(
        Instant startAt,
        Instant endAt,
        Integer configuredHours,
        long remainingSeconds,
        boolean elapsed,
        boolean exceptionApplied) {
}
