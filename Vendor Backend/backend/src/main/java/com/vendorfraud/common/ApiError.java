package com.vendorfraud.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The single error shape returned by every endpoint.
 *
 * @param timestamp when the failure was observed
 * @param status    HTTP status code
 * @param error     HTTP reason phrase
 * @param code      stable machine-readable business code
 * @param message   human-readable, safe to display
 * @param path      request path
 * @param fieldErrors per-field validation failures, when applicable
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        List<Map<String, String>> fieldErrors) {

    public static ApiError of(int status, String error, String code, String message, String path) {
        return new ApiError(Instant.now(), status, error, code, message, path, null);
    }
}
