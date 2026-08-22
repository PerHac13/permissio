package com.perhac.permissio.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 * <p>
 * Verifies that every custom and framework exception maps to the correct
 * HTTP status code and {@link ErrorResponse} shape.
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        MDC.clear();
    }

    // =========================================================================
    // Custom application exceptions
    // =========================================================================

    @Test
    void handleNotFound_returns404_withErrorResponse() {
        NotFoundException ex = new NotFoundException("Resource not found");
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("NOT_FOUND");
        assertThat(response.getBody().message()).isEqualTo("Resource not found");
        assertThat(response.getBody().traceId()).isEqualTo("N/A");
    }

    @Test
    void handleNotFound_withCause_returns404() {
        NotFoundException ex = new NotFoundException("Not found with cause", new RuntimeException("root"));
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).isEqualTo("Not found with cause");
    }

    @Test
    void handleConflict_returns409_withErrorResponse() {
        ConflictException ex = new ConflictException("Duplicate entity");
        ResponseEntity<ErrorResponse> response = handler.handleConflict(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONFLICT");
        assertThat(response.getBody().message()).isEqualTo("Duplicate entity");
    }

    @Test
    void handleUnauthorized_returns401_withErrorResponse() {
        UnauthorizedException ex = new UnauthorizedException("Unauthorized access");
        ResponseEntity<ErrorResponse> response = handler.handleUnauthorized(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getBody().message()).isEqualTo("Unauthorized access");
    }

    @Test
    void handleUnauthorized_withCause_returns401() {
        UnauthorizedException ex = new UnauthorizedException("Invalid token", new RuntimeException("expired"));
        ResponseEntity<ErrorResponse> response = handler.handleUnauthorized(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().message()).isEqualTo("Invalid token");
    }

    @Test
    void handleForbidden_returns403_withErrorResponse() {
        ForbiddenException ex = new ForbiddenException("Insufficient permissions");
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("Insufficient permissions");
    }

    @Test
    void handleForbidden_withCause_returns403() {
        ForbiddenException ex = new ForbiddenException("No access", new RuntimeException("policy denied"));
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().message()).isEqualTo("No access");
    }

    // =========================================================================
    // Spring Security exceptions
    // =========================================================================

    @Test
    void handleAccessDenied_returns403_withErrorResponse() {
        AccessDeniedException ex = new AccessDeniedException("Access denied");
        ResponseEntity<ErrorResponse> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
        assertThat(response.getBody().message()).isEqualTo("Access denied");
    }

    // =========================================================================
    // Validation exceptions
    // =========================================================================

    @Test
    void handleValidation_returns400_withAggregatedMessages() {
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);

        FieldError fieldError1 = new FieldError("object", "name", "must not be blank");
        FieldError fieldError2 = new FieldError("object", "apiKey", "is required");

        when(ex.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(fieldError1, fieldError2));

        ResponseEntity<ErrorResponse> response = handler.handleValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
        assertThat(response.getBody().message()).contains("name: must not be blank");
        assertThat(response.getBody().message()).contains("apiKey: is required");
    }

    // =========================================================================
    // Request parsing exceptions
    // =========================================================================

    @Test
    void handleMessageNotReadable_returns400_withMalformedRequestCode() {
        HttpMessageNotReadableException ex = mock(HttpMessageNotReadableException.class);
        ResponseEntity<ErrorResponse> response = handler.handleMessageNotReadable(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(response.getBody().message()).isEqualTo("Malformed JSON request body");
    }

    @Test
    void handleTypeMismatch_returns400_withTypeMismatchCode() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);
        when(ex.getName()).thenReturn("id");
        org.mockito.Mockito.doReturn(UUID.class).when(ex).getRequiredType();

        ResponseEntity<ErrorResponse> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("TYPE_MISMATCH");
        assertThat(response.getBody().message()).contains("id").contains("UUID");
    }

    @Test
    void handleMissingHeader_returns400_withMissingHeaderCode() {
        MissingRequestHeaderException ex = new MissingRequestHeaderException("X-API-Key", null);

        ResponseEntity<ErrorResponse> response = handler.handleMissingHeader(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("MISSING_HEADER");
        assertThat(response.getBody().message()).contains("X-API-Key");
    }

    // =========================================================================
    // HTTP method not supported
    // =========================================================================

    @Test
    void handleMethodNotSupported_returns405_withMethodNotAllowedCode() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("PATCH");

        ResponseEntity<ErrorResponse> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
        assertThat(response.getBody().message()).contains("PATCH");
    }

    // =========================================================================
    // Catch-all
    // =========================================================================

    @Test
    void handleGeneric_returns500_withErrorResponse() {
        Exception ex = new RuntimeException("Unexpected database failure");
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(response.getBody().message()).isEqualTo("Unexpected error");
    }

    // =========================================================================
    // Trace ID correlation
    // =========================================================================

    @Test
    void handleNotFound_withTraceIdInMdc_includesTraceId() {
        MDC.put("traceId", "trace-xyz-123");
        NotFoundException ex = new NotFoundException("Not found");
        ResponseEntity<ErrorResponse> response = handler.handleNotFound(ex);

        assertThat(response.getBody().traceId()).isEqualTo("trace-xyz-123");
    }

    @Test
    void handleForbidden_withTraceIdInMdc_includesTraceId() {
        MDC.put("traceId", "trace-abc-456");
        ForbiddenException ex = new ForbiddenException("Forbidden");
        ResponseEntity<ErrorResponse> response = handler.handleForbidden(ex);

        assertThat(response.getBody().traceId()).isEqualTo("trace-abc-456");
    }
}
