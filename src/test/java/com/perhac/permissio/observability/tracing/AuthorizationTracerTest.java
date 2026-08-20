package com.perhac.permissio.observability.tracing;

import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.subject.entity.Subject;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationTracerTest {

    @Mock
    private Tracer tracer;

    @Mock
    private Span span;

    @Mock
    private Tracer.SpanInScope spanInScope;

    private AuthorizationTracer authorizationTracer;
    private AuthorizationContext context;
    private UUID clientId;
    private UUID subjectId;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        authorizationTracer = new AuthorizationTracer(tracer);
        clientId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        resourceId = UUID.randomUUID();

        Subject subject = Subject.builder()
                .id(subjectId)
                .clientId(clientId)
                .externalId("user-1")
                .createdAt(Instant.now())
                .build();

        Resource resource = Resource.builder()
                .id(resourceId)
                .clientId(clientId)
                .resourceType("document")
                .externalId("doc-1")
                .createdAt(Instant.now())
                .build();

        context = new AuthorizationContext(clientId, subject, resource, Action.READ, Collections.emptyList());
    }

    @Test
    @DisplayName("traceEngine: starts span with context tags, records decision tags, and ends span")
    void traceEngine_startsTagsAndEndsSpan() {
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(spanInScope);

        Decision expectedDecision = Decision.allow("ALL_PASSED");

        Decision actual = authorizationTracer.traceEngine(context, () -> expectedDecision);

        assertThat(actual).isEqualTo(expectedDecision);

        verify(span).name("AuthorizationEngine.authorize");
        verify(span).tag("client.id", clientId.toString());
        verify(span).tag("subject.id", subjectId.toString());
        verify(span).tag("resource.id", resourceId.toString());
        verify(span).tag("authz.action", "READ");
        verify(span).tag("authz.decision", "true");
        verify(span).tag("authz.evaluator", "ALL_PASSED");
        verify(span).end();
    }

    @Test
    @DisplayName("traceEvaluator: tags evaluator name and denial reason when denied")
    void traceEvaluator_tagsEvaluatorAndDenialReason() {
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(anyString())).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(span)).thenReturn(spanInScope);

        Decision denyDecision = Decision.deny("NO_RELATIONSHIP", "REBAC");

        Decision actual = authorizationTracer.traceEvaluator("RebacEvaluator", context, () -> denyDecision);

        assertThat(actual).isEqualTo(denyDecision);

        verify(span).name("RebacEvaluator.evaluate");
        verify(span).tag("authz.evaluator", "RebacEvaluator");
        verify(span).tag("authz.decision", "false");
        verify(span).tag("authz.reason_code", "NO_RELATIONSHIP");
        verify(span).end();
    }
}
