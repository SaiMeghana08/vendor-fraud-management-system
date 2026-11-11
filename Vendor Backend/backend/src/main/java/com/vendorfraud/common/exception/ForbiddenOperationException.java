package com.vendorfraud.common.exception;

/**
 * The caller is authenticated but is not permitted to perform this
 * operation on this entity - separation of duties, assignment ownership
 * or recipient privacy. HTTP 403.
 */
public class ForbiddenOperationException extends RuntimeException {
    private final String code;

    public ForbiddenOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
