package com.perhac.permissio.common.exception;

/**
 * Thrown when authentication fails — invalid API key, expired JWT, etc.
 * Maps to HTTP 401.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
