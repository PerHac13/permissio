package com.perhac.permissio.common.exception;

/**
 * Thrown when a requested entity (Subject, Resource, Client, etc.) is not found.
 * Maps to HTTP 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
