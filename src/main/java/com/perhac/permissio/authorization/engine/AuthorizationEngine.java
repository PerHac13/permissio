package com.perhac.permissio.authorization.engine;

import com.perhac.permissio.audit.service.AuditService;
import com.perhac.permissio.authorization.evaluator.PolicyEvaluator;
import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.observability.metrics.AuthorizationMetrics;
import com.perhac.permissio.observability.tracing.AuthorizationTracer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Orchestrates the authorization evaluation pipeline across ordered {@link PolicyEvaluator}s.
 * <p>
 * Evaluators are executed in strict priority order (ReBAC ➔ ABAC ➔ Business Rules).
 * Evaluation short-circuits on the first evaluator that returns a denial.
 * Every decision (allowed or denied) is durably recorded via {@link AuditService},
 * instrumented with OpenTelemetry distributed tracing spans via {@link AuthorizationTracer},
 * and recorded in Prometheus/OTel metrics via {@link AuthorizationMetrics}.
 */
@Component
public class AuthorizationEngine {

    private final List<PolicyEvaluator> evaluators;
    private final AuditService auditService;
    private final AuthorizationTracer authorizationTracer;
    private final AuthorizationMetrics authorizationMetrics;

    public AuthorizationEngine(List<PolicyEvaluator> evaluators, AuditService auditService) {
        this(evaluators, auditService, null, null);
    }

    @Autowired
    public AuthorizationEngine(List<PolicyEvaluator> evaluators,
                               AuditService auditService,
                               @Autowired(required = false) AuthorizationTracer authorizationTracer,
                               @Autowired(required = false) AuthorizationMetrics authorizationMetrics) {
        this.evaluators = evaluators != null ? evaluators : List.of();
        this.auditService = auditService;
        this.authorizationTracer = authorizationTracer != null ? authorizationTracer : new AuthorizationTracer();
        this.authorizationMetrics = authorizationMetrics;
    }

    /**
     * Executes the authorization decision pipeline for the given context.
     *
     * @param context the assembled authorization context
     * @return the resulting Decision (allow or first denial)
     */
    public Decision authorize(AuthorizationContext context) {
        long startNanos = System.nanoTime();

        return authorizationTracer.traceEngine(context, () -> {
            for (PolicyEvaluator evaluator : evaluators) {
                Decision decision = authorizationTracer.traceEvaluator(
                        evaluator.name(),
                        context,
                        () -> evaluator.evaluate(context)
                );

                if (!decision.allowed()) {
                    recordAuditAndMetrics(context, decision, evaluator.name(), startNanos);
                    return decision;
                }
            }

            Decision allowed = Decision.allow("ALL_PASSED");
            recordAuditAndMetrics(context, allowed, "ALL_PASSED", startNanos);
            return allowed;
        });
    }

    private void recordAuditAndMetrics(AuthorizationContext context, Decision decision, String evaluatorName, long startNanos) {
        if (auditService != null) {
            auditService.log(context, decision, evaluatorName);
        }

        if (authorizationMetrics != null && context != null && context.subject() != null) {
            Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
            authorizationMetrics.recordDecision(
                    context.subject().getClientId(),
                    context.action(),
                    decision.allowed(),
                    evaluatorName,
                    decision.reason(),
                    duration
            );
        }
    }
}
