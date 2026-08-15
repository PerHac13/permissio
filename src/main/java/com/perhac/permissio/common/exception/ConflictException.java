package com.perhac.permissio.common.exception;

/**
 * Thrown when attempting to create a resource that already exists
 * (e.g., duplicate Subject registration within a tenant).
 * Maps to HTTP 409 Conflict.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
