package com.perhac.permissio.observability.tracing;

import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Helper for distributed tracing spans and semantic tagging around
 * the Authorization Engine and individual Policy Evaluators.
 */
@Component
public class AuthorizationTracer {

    private final Tracer tracer;

    public AuthorizationTracer() {
        this.tracer = null;
    }

    @Autowired
    public AuthorizationTracer(@Autowired(required = false) Tracer tracer) {
        this.tracer = tracer;
    }

    /**
     * Executes the authorization engine inside a parent OpenTelemetry span.
     */
    public Decision traceEngine(AuthorizationContext context, Supplier<Decision> execution) {
        if (tracer == null) {
            return execution.get();
        }

        Span span = tracer.nextSpan().name("AuthorizationEngine.authorize");
        tagContext(span, context);

        try (Tracer.SpanInScope ignored = tracer.withSpan(span.start())) {
            Decision decision = execution.get();
            tagDecision(span, decision);
            return decision;
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
        }
    }

    /**
     * Executes a policy evaluator inside a child OpenTelemetry span.
     */
    public Decision traceEvaluator(String evaluatorName, AuthorizationContext context, Supplier<Decision> execution) {
        if (tracer == null) {
            return execution.get();
        }

        Span span = tracer.nextSpan().name(evaluatorName + ".evaluate");
        span.tag("authz.evaluator", evaluatorName);
        tagContext(span, context);

        try (Tracer.SpanInScope ignored = tracer.withSpan(span.start())) {
            Decision decision = execution.get();
            tagDecision(span, decision);
            return decision;
        } catch (Throwable t) {
            span.error(t);
            throw t;
        } finally {
            span.end();
        }
    }

    private void tagContext(Span span, AuthorizationContext context) {
        if (context == null) return;
        if (context.subject() != null && context.subject().getClientId() != null) {
            span.tag("client.id", context.subject().getClientId().toString());
        }
        if (context.subject() != null && context.subject().getId() != null) {
            span.tag("subject.id", context.subject().getId().toString());
        }
        if (context.resource() != null && context.resource().getId() != null) {
            span.tag("resource.id", context.resource().getId().toString());
        }
        if (context.action() != null) {
            span.tag("authz.action", context.action().name());
        }
    }

    private void tagDecision(Span span, Decision decision) {
        if (decision == null) return;
        span.tag("authz.decision", String.valueOf(decision.allowed()));
        if (decision.evaluator() != null) {
            span.tag("authz.evaluator", decision.evaluator());
        }
        if (decision.reason() != null) {
            span.tag("authz.reason_code", decision.reason());
        }
    }
}
