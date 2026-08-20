package com.perhac.permissio.audit.repository;

import com.perhac.permissio.audit.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AuditLog} entities.
 * <p>
 * Enforces tenant isolation by requiring {@code clientId} across all queries.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    /**
     * Lists audit logs for a tenant with pagination.
     */
    Page<AuditLog> findByClientId(UUID clientId, Pageable pageable);

    /**
     * Lists audit logs filtered by subject ID for a tenant.
     */
    Page<AuditLog> findByClientIdAndSubjectId(UUID clientId, UUID subjectId, Pageable pageable);

    /**
     * Lists audit logs filtered by resource ID for a tenant.
     */
    Page<AuditLog> findByClientIdAndResourceId(UUID clientId, UUID resourceId, Pageable pageable);

    /**
     * Lists audit logs filtered by subject ID and resource ID for a tenant.
     */
    Page<AuditLog> findByClientIdAndSubjectIdAndResourceId(
            UUID clientId, UUID subjectId, UUID resourceId, Pageable pageable);

    /**
     * Lists all audit logs for a tenant.
     */
    List<AuditLog> findAllByClientId(UUID clientId);
}
