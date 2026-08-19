package com.perhac.permissio.audit.service;

import com.perhac.permissio.audit.entity.AuditLog;
import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.subject.entity.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

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
                .createdAt(Instant.now())
                .build();

        resource = Resource.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("document")
                .externalId("doc-1")
                .createdAt(Instant.now())
                .build();

        context = new AuthorizationContext(clientId, subject, resource, Action.UPDATE, List.of());
    }

    @Test
    @DisplayName("Logs an allowed decision correctly with trace correlation")
    void log_allowedDecision_persistsAuditLog() {
        MDC.put("trace_id", "trace-abc-123");
        try {
            Decision decision = Decision.allow("REBAC");
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

            AuditLog saved = auditService.log(context, decision, "ALL_PASSED");

            assertThat(saved.getClientId()).isEqualTo(clientId);
            assertThat(saved.getSubjectId()).isEqualTo(subject.getId());
            assertThat(saved.getResourceId()).isEqualTo(resource.getId());
            assertThat(saved.getAction()).isEqualTo(Action.UPDATE);
            assertThat(saved.isAllowed()).isTrue();
            assertThat(saved.getReason()).isNull();
            assertThat(saved.getEvaluator()).isEqualTo("ALL_PASSED");
            assertThat(saved.getTraceId()).isEqualTo("trace-abc-123");

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditLogRepository).save(captor.capture());
            assertThat(captor.getValue().isAllowed()).isTrue();
        } finally {
            MDC.clear();
        }
    }

    @Test
    @DisplayName("Logs a denied decision correctly with reason and evaluator")
    void log_deniedDecision_persistsAuditLog() {
        Decision decision = Decision.deny("RELATION_INSUFFICIENT", "REBAC");
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AuditLog saved = auditService.log(context, decision, "REBAC");

        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.isAllowed()).isFalse();
        assertThat(saved.getReason()).isEqualTo("RELATION_INSUFFICIENT");
        assertThat(saved.getEvaluator()).isEqualTo("REBAC");
    }
}
