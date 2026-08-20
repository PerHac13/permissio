package com.perhac.permissio.observability.filter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceContextFilterTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private TraceContext traceContext;

    @Mock
    private FilterChain filterChain;

    private TraceContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceContextFilter(tracer);
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Populates MDC with active trace_id and span_id and sets X-Trace-Id response header")
    void doFilterInternal_withActiveTracer_populatesMdcAndResponseHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(traceContext);
        when(traceContext.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        when(traceContext.spanId()).thenReturn("00f067aa0ba902b7");

        filter.doFilterInternal(request, response, (req, res) -> {
            // Inside the filter chain, MDC must contain trace_id and span_id
            assertThat(MDC.get("trace_id")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(MDC.get("span_id")).isEqualTo("00f067aa0ba902b7");
        });

        // Response header must be set
        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");

        // After request completion, MDC must be cleared
        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("span_id")).isNull();
    }

    @Test
    @DisplayName("Generates fallback trace_id when tracer has no current span")
    void doFilterInternal_withoutActiveSpan_generatesFallbackTraceId() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(tracer.currentSpan()).thenReturn(null);

        filter.doFilterInternal(request, response, (req, res) -> {
            assertThat(MDC.get("trace_id")).isNotBlank();
        });

        assertThat(response.getHeader("X-Trace-Id")).isNotBlank();
        assertThat(MDC.get("trace_id")).isNull();
    }
}
