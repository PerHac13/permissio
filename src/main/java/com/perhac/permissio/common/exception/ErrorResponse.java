package com.perhac.permissio.common.exception;

/**
 * Standard error response envelope returned by all API error responses.
 * Consistent structure for debuggability by client teams (per PRD Section 10).
 */
public record ErrorResponse(
        String code,
        String message,
        String traceId
) {
}
