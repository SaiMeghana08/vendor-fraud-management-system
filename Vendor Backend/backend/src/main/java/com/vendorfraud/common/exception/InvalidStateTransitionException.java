package com.vendorfraud.common.exception;

/** An out-of-order or repeated workflow operation. HTTP 409. */
public class InvalidStateTransitionException extends RuntimeException {
    private final String code;

    public InvalidStateTransitionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public InvalidStateTransitionException(String message) {
        this("INVALID_STATE_TRANSITION", message);
    }

    public String getCode() {
        return code;
    }
}
