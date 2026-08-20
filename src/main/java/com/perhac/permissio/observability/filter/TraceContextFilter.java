package com.perhac.permissio.observability.filter;

import com.perhac.permissio.security.TenantContext;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filter that populates the SLF4J MDC with active OpenTelemetry {@code trace_id},
 * {@code span_id}, and {@code clientId} for structured log correlation.
 * <p>
 * Also adds the {@code X-Trace-Id} response header for client-side trace correlation.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "trace_id";
    public static final String SPAN_ID_KEY = "span_id";
    public static final String CLIENT_ID_KEY = "clientId";
    public static final String TRACE_HEADER = "X-Trace-Id";

    private final Tracer tracer;

    public TraceContextFilter() {
        this.tracer = null;
    }

    @Autowired
    public TraceContextFilter(@Autowired(required = false) Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = resolveTraceId();
        String spanId = resolveSpanId();

        MDC.put(TRACE_ID_KEY, traceId);
        if (spanId != null) {
            MDC.put(SPAN_ID_KEY, spanId);
        }

        UUID tenantId = TenantContext.get();
        if (tenantId != null) {
            MDC.put(CLIENT_ID_KEY, tenantId.toString());
        }

        response.setHeader(TRACE_HEADER, traceId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
            MDC.remove(SPAN_ID_KEY);
            MDC.remove(CLIENT_ID_KEY);
        }
    }

    private String resolveTraceId() {
        if (tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null && currentSpan.context().traceId() != null) {
                return currentSpan.context().traceId();
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String resolveSpanId() {
        if (tracer != null) {
            Span currentSpan = tracer.currentSpan();
            if (currentSpan != null && currentSpan.context() != null && currentSpan.context().spanId() != null) {
                return currentSpan.context().spanId();
            }
        }
        return null;
    }
}
