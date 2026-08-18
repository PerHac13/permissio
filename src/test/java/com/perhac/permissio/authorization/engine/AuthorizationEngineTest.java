package com.perhac.permissio.authorization.engine;

import com.perhac.permissio.audit.service.AuditService;
import com.perhac.permissio.authorization.evaluator.PolicyEvaluator;
import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.subject.entity.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationEngineTest {

    @Mock
    private PolicyEvaluator rebacEvaluator;

    @Mock
    private PolicyEvaluator abacEvaluator;

    @Mock
    private PolicyEvaluator businessRuleEvaluator;

    @Mock
    private AuditService auditService;

    private AuthorizationEngine authorizationEngine;
    private AuthorizationContext context;

    @BeforeEach
    void setUp() {
        UUID clientId = UUID.randomUUID();
        Subject subject = Subject.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .externalId("alice")
                .passwordHash("pwd")
                .createdAt(Instant.now())
                .build();

        Resource resource = Resource.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("document")
                .externalId("doc-1")
                .createdAt(Instant.now())
                .build();

        context = new AuthorizationContext(clientId, subject, resource, Action.READ, List.of());
    }

    @Test
    @DisplayName("Returns allowed decision and logs audit when all evaluators pass in sequence")
    void authorize_allEvaluatorsPass_returnsAllowedDecisionAndLogsAudit() {
        when(rebacEvaluator.evaluate(context)).thenReturn(Decision.allow("REBAC"));
        when(abacEvaluator.evaluate(context)).thenReturn(Decision.allow("ABAC"));
        when(businessRuleEvaluator.evaluate(context)).thenReturn(Decision.allow("BUSINESS_RULE"));

        authorizationEngine = new AuthorizationEngine(
                List.of(rebacEvaluator, abacEvaluator, businessRuleEvaluator),
                auditService
        );

        Decision result = authorizationEngine.authorize(context);

        assertThat(result.allowed()).isTrue();
        assertThat(result.reason()).isNull();
        assertThat(result.evaluator()).isEqualTo("ALL_PASSED");

        InOrder inOrder = inOrder(rebacEvaluator, abacEvaluator, businessRuleEvaluator);
        inOrder.verify(rebacEvaluator).evaluate(context);
        inOrder.verify(abacEvaluator).evaluate(context);
        inOrder.verify(businessRuleEvaluator).evaluate(context);

        verify(auditService).log(eq(context), any(Decision.class), eq("ALL_PASSED"));
    }

    @Test
    @DisplayName("Short-circuits immediately and logs audit when ReBAC evaluator denies")
    void authorize_rebacDenies_shortCircuitsAndLogsAudit() {
        Decision rebacDeny = Decision.deny("NO_RELATIONSHIP", "REBAC");
        when(rebacEvaluator.evaluate(context)).thenReturn(rebacDeny);
        when(rebacEvaluator.name()).thenReturn("REBAC");

        authorizationEngine = new AuthorizationEngine(
                List.of(rebacEvaluator, abacEvaluator, businessRuleEvaluator),
                auditService
        );

        Decision result = authorizationEngine.authorize(context);

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("NO_RELATIONSHIP");
        assertThat(result.evaluator()).isEqualTo("REBAC");

        verify(rebacEvaluator).evaluate(context);
        verifyNoInteractions(abacEvaluator);
        verifyNoInteractions(businessRuleEvaluator);

        verify(auditService).log(context, rebacDeny, "REBAC");
    }

    @Test
    @DisplayName("Short-circuits when ABAC evaluator denies after ReBAC passes")
    void authorize_abacDenies_shortCircuitsAndLogsAudit() {
        Decision abacDeny = Decision.deny("DEPT_MISMATCH", "ABAC");
        when(rebacEvaluator.evaluate(context)).thenReturn(Decision.allow("REBAC"));
        when(abacEvaluator.evaluate(context)).thenReturn(abacDeny);
        when(abacEvaluator.name()).thenReturn("ABAC");

        authorizationEngine = new AuthorizationEngine(
                List.of(rebacEvaluator, abacEvaluator, businessRuleEvaluator),
                auditService
        );

        Decision result = authorizationEngine.authorize(context);

        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).isEqualTo("DEPT_MISMATCH");
        assertThat(result.evaluator()).isEqualTo("ABAC");

        verify(rebacEvaluator).evaluate(context);
        verify(abacEvaluator).evaluate(context);
        verify(businessRuleEvaluator, never()).evaluate(any());

        verify(auditService).log(context, abacDeny, "ABAC");
    }

    @Test
    @DisplayName("Handles empty evaluators list by returning allowed decision and logging audit")
    void authorize_emptyEvaluators_returnsAllowedDecision() {
        authorizationEngine = new AuthorizationEngine(List.of(), auditService);

        Decision result = authorizationEngine.authorize(context);

        assertThat(result.allowed()).isTrue();
        assertThat(result.evaluator()).isEqualTo("ALL_PASSED");
        verify(auditService).log(eq(context), any(Decision.class), eq("ALL_PASSED"));
    }
}
