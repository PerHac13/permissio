package com.perhac.permissio.authorization.engine;

import com.perhac.permissio.audit.service.AuditService;
import com.perhac.permissio.authorization.evaluator.PolicyEvaluator;
import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Orchestrates the authorization evaluation pipeline across ordered {@link PolicyEvaluator}s.
 * <p>
 * Evaluators are executed in strict priority order (ReBAC ➔ ABAC ➔ Business Rules).
 * Evaluation short-circuits on the first evaluator that returns a denial.
 * Every decision (allowed or denied) is durably recorded via {@link AuditService}.
 */
@Component
public class AuthorizationEngine {

    private final List<PolicyEvaluator> evaluators;
    private final AuditService auditService;

    public AuthorizationEngine(List<PolicyEvaluator> evaluators, AuditService auditService) {
        this.evaluators = evaluators != null ? evaluators : List.of();
        this.auditService = auditService;
    }

    /**
     * Executes the authorization decision pipeline for the given context.
     *
     * @param context the assembled authorization context
     * @return the resulting Decision (allow or first denial)
     */
    public Decision authorize(AuthorizationContext context) {
        for (PolicyEvaluator evaluator : evaluators) {
            Decision decision = evaluator.evaluate(context);
            if (!decision.allowed()) {
                if (auditService != null) {
                    auditService.log(context, decision, evaluator.name());
                }
                return decision;
            }
        }

        Decision allowed = Decision.allow("ALL_PASSED");
        if (auditService != null) {
            auditService.log(context, allowed, "ALL_PASSED");
        }
        return allowed;
    }
}
