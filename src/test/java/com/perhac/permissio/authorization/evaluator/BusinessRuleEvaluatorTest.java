package com.perhac.permissio.authorization.evaluator;

import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.policy.engine.PolicyEvaluationEngine;
import com.perhac.permissio.policy.entity.Policy;
import com.perhac.permissio.policy.entity.PolicyType;
import com.perhac.permissio.policy.repository.PolicyRepository;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.subject.entity.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BusinessRuleEvaluatorTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyEvaluationEngine policyEngine;

    private Clock fixedClock;
    private BusinessRuleEvaluator businessRuleEvaluator;

    private UUID clientId;
    private Subject subject;
    private Resource resource;
    private AuthorizationContext context;

    @BeforeEach
    void setUp() {
        // Fixed at 14:00 (2 PM) UTC
        fixedClock = Clock.fixed(Instant.parse("2026-08-18T14:00:00Z"), ZoneId.of("UTC"));
        businessRuleEvaluator = new BusinessRuleEvaluator(policyRepository, policyEngine, fixedClock);

        clientId = UUID.randomUUID();
        subject = Subject.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .externalId("alice")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();

        resource = Resource.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("document")
                .externalId("doc-1")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();

        context = new AuthorizationContext(clientId, subject, resource, Action.DELETE, List.of());
    }

    @Test
    @DisplayName("Returns allow when no business rule policies exist")
    void evaluate_noPolicies_returnsAllow() {
        when(policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                clientId, "document", Action.DELETE, PolicyType.BUSINESS_RULE
        )).thenReturn(List.of());

        Decision decision = businessRuleEvaluator.evaluate(context);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.evaluator()).isEqualTo("BUSINESS_RULE");
    }

    @Test
    @DisplayName("Returns allow when time-window business rule evaluates to true")
    void evaluate_timeWindowPasses_returnsAllow() {
        Policy policy = Policy.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("document")
                .action(Action.DELETE)
                .policyType(PolicyType.BUSINESS_RULE)
                .expression("#environment['currentHour'] >= 9 and #environment['currentHour'] < 17")
                .build();

        when(policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                clientId, "document", Action.DELETE, PolicyType.BUSINESS_RULE
        )).thenReturn(List.of(policy));

        when(policyEngine.evaluate(
                eq(policy.getExpression()),
                eq("{}"),
                eq("{}"),
                eq("DELETE"),
                any()
        )).thenReturn(true);

        Decision decision = businessRuleEvaluator.evaluate(context);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.evaluator()).isEqualTo("BUSINESS_RULE");
    }

    @Test
    @DisplayName("Returns deny when business rule evaluates to false")
    void evaluate_timeWindowFails_returnsDeny() {
        Policy policy = Policy.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("document")
                .action(Action.DELETE)
                .policyType(PolicyType.BUSINESS_RULE)
                .expression("#environment['currentHour'] < 12")
                .build();

        when(policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                clientId, "document", Action.DELETE, PolicyType.BUSINESS_RULE
        )).thenReturn(List.of(policy));

        when(policyEngine.evaluate(
                eq(policy.getExpression()),
                eq("{}"),
                eq("{}"),
                eq("DELETE"),
                any()
        )).thenReturn(false);

        Decision decision = businessRuleEvaluator.evaluate(context);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("BUSINESS_RULE_FAILED");
        assertThat(decision.evaluator()).isEqualTo("BUSINESS_RULE");
    }

    @Test
    void metadata_verifiesNameAndOrder() {
        assertThat(businessRuleEvaluator.name()).isEqualTo("BUSINESS_RULE");
        assertThat(businessRuleEvaluator.getOrder()).isEqualTo(3);
    }
}
