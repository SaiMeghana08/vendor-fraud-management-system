package com.vendorfraud.common.exception;

/** The referenced entity does not exist or is not visible. HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String entity, Object id) {
        super(entity + " not found: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
