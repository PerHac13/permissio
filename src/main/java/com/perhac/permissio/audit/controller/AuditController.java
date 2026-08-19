package com.perhac.permissio.audit.controller;

import com.perhac.permissio.audit.dto.AuditLogResponse;
import com.perhac.permissio.audit.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for tenant-scoped Audit Log queries.
 */
@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /**
     * Lists and filters immutable audit logs for the current tenant.
     *
     * @param subjectId  optional filter by Subject UUID
     * @param resourceId optional filter by Resource UUID
     * @param page       zero-based page index (default: 0)
     * @param size       page size (default: 20)
     * @param sortBy     sort field (default: "createdAt")
     * @param direction  sort direction "asc" or "desc" (default: "desc")
     * @return paginated list of {@link AuditLogResponse}
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogResponse>> listAuditLogs(
            @RequestParam(name = "subjectId", required = false) UUID subjectId,
            @RequestParam(name = "resourceId", required = false) UUID resourceId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "sortBy", defaultValue = "createdAt") String sortBy,
            @RequestParam(name = "direction", defaultValue = "desc") String direction) {

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(sortDirection, sortBy));

        Page<AuditLogResponse> response = auditService.listAuditLogs(subjectId, resourceId, pageRequest);
        return ResponseEntity.ok(response);
    }
}
