package com.perhac.permissio.audit.dto;

import com.perhac.permissio.audit.entity.AuditLog;
import com.perhac.permissio.common.model.Action;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for Audit Log queries.
 */
public record AuditLogResponse(
        UUID id,
        UUID clientId,
        UUID subjectId,
        UUID resourceId,
        Action action,
        boolean allowed,
        String reason,
        String evaluator,
        String traceId,
        Instant createdAt
) {
    public static AuditLogResponse fromEntity(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getClientId(),
                log.getSubjectId(),
                log.getResourceId(),
                log.getAction(),
                log.isAllowed(),
                log.getReason(),
                log.getEvaluator(),
                log.getTraceId(),
                log.getCreatedAt()
        );
    }
}
