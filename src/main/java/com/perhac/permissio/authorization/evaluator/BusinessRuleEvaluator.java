package com.perhac.permissio.authorization.evaluator;

import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.policy.engine.PolicyEvaluationEngine;
import com.perhac.permissio.policy.entity.Policy;
import com.perhac.permissio.policy.entity.PolicyType;
import com.perhac.permissio.policy.repository.PolicyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Business Rule evaluator (Order 3).
 * <p>
 * Queries tenant-scoped {@link Policy} records with type {@link PolicyType#BUSINESS_RULE}
 * and evaluates them against environmental variables (e.g. time window, day of week)
 * and Subject / Resource context.
 */
@Component
@Order(3)
public class BusinessRuleEvaluator implements PolicyEvaluator {

    public static final String EVALUATOR_NAME = "BUSINESS_RULE";
    public static final String REASON_POLICY_FAILED = "BUSINESS_RULE_FAILED";

    private final PolicyRepository policyRepository;
    private final PolicyEvaluationEngine policyEvaluationEngine;
    private final Clock clock;

    @Autowired
    public BusinessRuleEvaluator(
            PolicyRepository policyRepository,
            PolicyEvaluationEngine policyEvaluationEngine) {
        this(policyRepository, policyEvaluationEngine, Clock.systemUTC());
    }

    public BusinessRuleEvaluator(
            PolicyRepository policyRepository,
            PolicyEvaluationEngine policyEvaluationEngine,
            Clock clock) {
        this.policyRepository = policyRepository;
        this.policyEvaluationEngine = policyEvaluationEngine;
        this.clock = clock != null ? clock : Clock.systemUTC();
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
                PolicyType.BUSINESS_RULE
        );

        if (policies.isEmpty()) {
            return Decision.allow(EVALUATOR_NAME);
        }

        String subjectAttrs = context.subject() != null ? context.subject().getAttributes() : "{}";
        String resourceAttrs = context.resource().getAttributes();
        String actionName = context.action() != null ? context.action().name() : "";

        Map<String, Object> environment = buildEnvironmentContext();

        for (Policy policy : policies) {
            boolean passed = policyEvaluationEngine.evaluate(
                    policy.getExpression(),
                    subjectAttrs,
                    resourceAttrs,
                    actionName,
                    environment
            );

            if (!passed) {
                return Decision.deny(REASON_POLICY_FAILED, EVALUATOR_NAME);
            }
        }

        return Decision.allow(EVALUATOR_NAME);
    }

    private Map<String, Object> buildEnvironmentContext() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        Map<String, Object> env = new HashMap<>();
        env.put("currentHour", now.getHour());
        env.put("currentMinute", now.getMinute());
        env.put("dayOfWeek", now.getDayOfWeek().getValue());
        env.put("timestamp", now.toInstant().toEpochMilli());
        return env;
    }

    @Override
    public String name() {
        return EVALUATOR_NAME;
    }

    @Override
    public int getOrder() {
        return 3;
    }
}
