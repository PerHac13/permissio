package com.perhac.permissio.common.exception;

/**
 * Thrown when an authenticated user lacks the required permissions for an action.
 * Maps to HTTP 403 Forbidden.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }

    public ForbiddenException(String message, Throwable cause) {
        super(message, cause);
    }
}
