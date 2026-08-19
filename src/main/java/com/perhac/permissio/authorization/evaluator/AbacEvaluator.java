package com.perhac.permissio.authorization.evaluator;

import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.policy.engine.PolicyEvaluationEngine;
import com.perhac.permissio.policy.entity.Policy;
import com.perhac.permissio.policy.entity.PolicyType;
import com.perhac.permissio.policy.repository.PolicyRepository;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Attribute-Based Access Control (ABAC) evaluator (Order 2).
 * <p>
 * Queries tenant-scoped {@link Policy} records with type {@link PolicyType#ABAC}
 * and evaluates their sandboxed SpEL expressions against Subject & Resource attributes.
 */
@Component
@Order(2)
public class AbacEvaluator implements PolicyEvaluator {

    public static final String EVALUATOR_NAME = "ABAC";
    public static final String REASON_POLICY_FAILED = "ABAC_POLICY_FAILED";

    private final PolicyRepository policyRepository;
    private final PolicyEvaluationEngine policyEvaluationEngine;

    public AbacEvaluator(
            PolicyRepository policyRepository,
            PolicyEvaluationEngine policyEvaluationEngine) {
        this.policyRepository = policyRepository;
        this.policyEvaluationEngine = policyEvaluationEngine;
    }

    @Override
    public Decision evaluate(AuthorizationContext context) {
        if (context == null || context.resource() == null) {
            return Decision.allow(EVALUATOR_NAME);
        }

        List<Policy> policies = policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                context.clientId(),
                context.resource().getResourceType(),
                context.action(),
                PolicyType.ABAC
        );

        if (policies.isEmpty()) {
            return Decision.allow(EVALUATOR_NAME);
        }

        String subjectAttrs = context.subject() != null ? context.subject().getAttributes() : "{}";
        String resourceAttrs = context.resource().getAttributes();
        String actionName = context.action() != null ? context.action().name() : "";

        for (Policy policy : policies) {
            boolean passed = policyEvaluationEngine.evaluate(
                    policy.getExpression(),
                    subjectAttrs,
                    resourceAttrs,
                    actionName,
                    Collections.emptyMap()
            );

            if (!passed) {
                return Decision.deny(REASON_POLICY_FAILED, EVALUATOR_NAME);
            }
        }

        return Decision.allow(EVALUATOR_NAME);
    }

    @Override
    public String name() {
        return EVALUATOR_NAME;
    }

    @Override
    public int getOrder() {
        return 2;
    }
}
