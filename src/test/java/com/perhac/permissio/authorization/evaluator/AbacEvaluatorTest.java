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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbacEvaluatorTest {

    @Mock
    private PolicyRepository policyRepository;

    @Mock
    private PolicyEvaluationEngine policyEngine;

    @InjectMocks
    private AbacEvaluator abacEvaluator;

    private UUID clientId;
    private Subject subject;
    private Resource resource;
    private AuthorizationContext context;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        subject = Subject.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .externalId("alice")
                .attributes("{\"dept\":\"engineering\"}")
                .createdAt(Instant.now())
                .build();

        resource = Resource.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("document")
                .externalId("doc-1")
                .attributes("{\"dept\":\"engineering\"}")
                .createdAt(Instant.now())
                .build();

        context = new AuthorizationContext(clientId, subject, resource, Action.UPDATE, List.of());
    }

    @Test
    @DisplayName("Returns allow when no ABAC policies exist for this resource type and action")
    void evaluate_noPolicies_returnsAllow() {
        when(policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                clientId, "document", Action.UPDATE, PolicyType.ABAC
        )).thenReturn(List.of());

        Decision decision = abacEvaluator.evaluate(context);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.evaluator()).isEqualTo("ABAC");
    }

    @Test
    @DisplayName("Returns allow when all matching ABAC policies evaluate to true")
    void evaluate_policyPasses_returnsAllow() {
        Policy policy = Policy.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("document")
                .action(Action.UPDATE)
                .policyType(PolicyType.ABAC)
                .expression("#subject['dept'] == #resource['dept']")
                .build();

        when(policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                clientId, "document", Action.UPDATE, PolicyType.ABAC
        )).thenReturn(List.of(policy));

        when(policyEngine.evaluate(
                eq(policy.getExpression()),
                eq(subject.getAttributes()),
                eq(resource.getAttributes()),
                eq("UPDATE"),
                any()
        )).thenReturn(true);

        Decision decision = abacEvaluator.evaluate(context);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isNull();
        assertThat(decision.evaluator()).isEqualTo("ABAC");
    }

    @Test
    @DisplayName("Returns deny when an ABAC policy evaluates to false")
    void evaluate_policyFails_returnsDeny() {
        Policy policy = Policy.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("document")
                .action(Action.UPDATE)
                .policyType(PolicyType.ABAC)
                .expression("#subject['dept'] == #resource['dept']")
                .build();

        when(policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                clientId, "document", Action.UPDATE, PolicyType.ABAC
        )).thenReturn(List.of(policy));

        when(policyEngine.evaluate(
                eq(policy.getExpression()),
                eq(subject.getAttributes()),
                eq(resource.getAttributes()),
                eq("UPDATE"),
                any()
        )).thenReturn(false);

        Decision decision = abacEvaluator.evaluate(context);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("ABAC_POLICY_FAILED");
        assertThat(decision.evaluator()).isEqualTo("ABAC");
    }

    @Test
    @DisplayName("Returns deny when one of multiple ABAC policies fails")
    void evaluate_multiplePolicies_oneFails_returnsDeny() {
        Policy policy1 = Policy.builder().id(UUID.randomUUID()).expression("expr1").build();
        Policy policy2 = Policy.builder().id(UUID.randomUUID()).expression("expr2").build();

        when(policyRepository.findByClientIdAndResourceTypeAndActionAndPolicyType(
                clientId, "document", Action.UPDATE, PolicyType.ABAC
        )).thenReturn(List.of(policy1, policy2));

        when(policyEngine.evaluate(eq("expr1"), any(), any(), any(), any())).thenReturn(true);
        when(policyEngine.evaluate(eq("expr2"), any(), any(), any(), any())).thenReturn(false);

        Decision decision = abacEvaluator.evaluate(context);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("ABAC_POLICY_FAILED");
    }

    @Test
    void metadata_verifiesNameAndOrder() {
        assertThat(abacEvaluator.name()).isEqualTo("ABAC");
        assertThat(abacEvaluator.getOrder()).isEqualTo(2);
    }
}
