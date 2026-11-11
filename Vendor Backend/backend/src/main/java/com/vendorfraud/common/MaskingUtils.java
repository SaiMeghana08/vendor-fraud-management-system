package com.vendorfraud.common;

/**
 * Masks sensitive banking references before they leave the service
 * layer. Nothing in this application ever returns a full account
 * reference to a client, including in audit output.
 */
public final class MaskingUtils {

    private MaskingUtils() {
    }

    /**
     * Keeps the last four characters and replaces the rest with bullets.
     * {@code SYN-AC-100001} becomes {@code •••••••••0001}.
     */
    public static String maskAccountReference(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        String trimmed = reference.trim();
        if (trimmed.length() <= 4) {
            return "•".repeat(trimmed.length());
        }
        int visible = 4;
        return "•".repeat(trimmed.length() - visible) + trimmed.substring(trimmed.length() - visible);
    }
}
