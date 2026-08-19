package com.perhac.permissio.audit.service;

import com.perhac.permissio.audit.dto.AuditLogResponse;
import com.perhac.permissio.audit.entity.AuditLog;
import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.security.TenantContext;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Service for creating and querying immutable audit logs for authorization decisions.
 */
@Service
@Transactional
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Persists an audit log entry for an authorization decision.
     *
     * @param context       the authorization context
     * @param decision      the outcome decision
     * @param evaluatorName the evaluator that made the final decision
     * @return the saved {@link AuditLog}
     */
    public AuditLog log(AuthorizationContext context, Decision decision, String evaluatorName) {
        String traceId = MDC.get("trace_id");
        if (traceId == null) {
            traceId = MDC.get("traceId");
        }

        AuditLog auditLog = AuditLog.builder()
                .clientId(context != null ? context.clientId() : null)
                .subjectId(context != null && context.subject() != null ? context.subject().getId() : null)
                .resourceId(context != null && context.resource() != null ? context.resource().getId() : null)
                .action(context != null ? context.action() : null)
                .allowed(decision != null && decision.allowed())
                .reason(decision != null ? decision.reason() : null)
                .evaluator(evaluatorName != null ? evaluatorName : (decision != null ? decision.evaluator() : null))
                .traceId(traceId)
                .createdAt(Instant.now())
                .build();

        return auditLogRepository.save(auditLog);
    }

    /**
     * Queries audit logs for the calling tenant with optional filtering.
     */
    @Transactional(readOnly = true)
    public Page<AuditLogResponse> listAuditLogs(UUID subjectId, UUID resourceId, Pageable pageable) {
        UUID clientId = TenantContext.get();

        Page<AuditLog> page;
        if (subjectId != null && resourceId != null) {
            page = auditLogRepository.findByClientIdAndSubjectIdAndResourceId(clientId, subjectId, resourceId, pageable);
        } else if (subjectId != null) {
            page = auditLogRepository.findByClientIdAndSubjectId(clientId, subjectId, pageable);
        } else if (resourceId != null) {
            page = auditLogRepository.findByClientIdAndResourceId(clientId, resourceId, pageable);
        } else {
            page = auditLogRepository.findByClientId(clientId, pageable);
        }

        return page.map(AuditLogResponse::fromEntity);
    }
}
