package com.perhac.permissio.audit.repository;

import com.perhac.permissio.audit.entity.AuditLog;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.common.model.Action;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class AuditLogRepositoryTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ClientRepository clientRepository;

    private Client clientA;
    private Client clientB;
    private UUID subjectId;
    private UUID resourceId;

    @BeforeEach
    void setUp() {
        auditLogRepository.deleteAll();
        clientRepository.deleteAll();

        clientA = clientRepository.save(Client.builder()
                .name("Tenant A")
                .apiKeyHash("hash-a")
                .createdAt(Instant.now())
                .build());

        clientB = clientRepository.save(Client.builder()
                .name("Tenant B")
                .apiKeyHash("hash-b")
                .createdAt(Instant.now())
                .build());

        subjectId = UUID.randomUUID();
        resourceId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Saves and retrieves an AuditLog entity")
    void saveAndRetrieveAuditLog() {
        AuditLog log = auditLogRepository.save(AuditLog.builder()
                .clientId(clientA.getId())
                .subjectId(subjectId)
                .resourceId(resourceId)
                .action(Action.UPDATE)
                .allowed(true)
                .evaluator("ALL_PASSED")
                .traceId("trace-123")
                .createdAt(Instant.now())
                .build());

        assertThat(log.getId()).isNotNull();

        List<AuditLog> logs = auditLogRepository.findAllByClientId(clientA.getId());
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getAction()).isEqualTo(Action.UPDATE);
        assertThat(logs.get(0).isAllowed()).isTrue();
        assertThat(logs.get(0).getTraceId()).isEqualTo("trace-123");
    }

    @Test
    @DisplayName("Tenant isolation: Client A cannot see Client B's audit logs")
    void tenantIsolation_cannotQueryOtherTenantsAuditLogs() {
        auditLogRepository.save(AuditLog.builder()
                .clientId(clientB.getId())
                .subjectId(subjectId)
                .resourceId(resourceId)
                .action(Action.DELETE)
                .allowed(false)
                .reason("RELATION_INSUFFICIENT")
                .evaluator("REBAC")
                .createdAt(Instant.now())
                .build());

        Page<AuditLog> aLogs = auditLogRepository.findByClientId(clientA.getId(), PageRequest.of(0, 10));
        assertThat(aLogs.getContent()).isEmpty();

        Page<AuditLog> bLogs = auditLogRepository.findByClientId(clientB.getId(), PageRequest.of(0, 10));
        assertThat(bLogs.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("Filters audit logs by subjectId and resourceId")
    void filterBySubjectAndResource() {
        UUID otherSubjectId = UUID.randomUUID();

        auditLogRepository.save(AuditLog.builder()
                .clientId(clientA.getId())
                .subjectId(subjectId)
                .resourceId(resourceId)
                .action(Action.READ)
                .allowed(true)
                .evaluator("ALL_PASSED")
                .createdAt(Instant.now())
                .build());

        auditLogRepository.save(AuditLog.builder()
                .clientId(clientA.getId())
                .subjectId(otherSubjectId)
                .resourceId(resourceId)
                .action(Action.UPDATE)
                .allowed(false)
                .reason("NO_RELATIONSHIP")
                .evaluator("REBAC")
                .createdAt(Instant.now())
                .build());

        Page<AuditLog> subjectLogs = auditLogRepository.findByClientIdAndSubjectId(
                clientA.getId(), subjectId, PageRequest.of(0, 10)
        );
        assertThat(subjectLogs.getContent()).hasSize(1);
        assertThat(subjectLogs.getContent().get(0).getSubjectId()).isEqualTo(subjectId);
    }
}
