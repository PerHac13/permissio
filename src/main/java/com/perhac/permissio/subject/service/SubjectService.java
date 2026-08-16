package com.perhac.permissio.subject.service;

import com.perhac.permissio.common.exception.ConflictException;
import com.perhac.permissio.common.exception.NotFoundException;
import com.perhac.permissio.security.TenantContext;
import com.perhac.permissio.subject.dto.CreateSubjectRequest;
import com.perhac.permissio.subject.dto.SubjectResponse;
import com.perhac.permissio.subject.entity.Subject;
import com.perhac.permissio.subject.repository.SubjectRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic for tenant-scoped Subject CRUD and attribute management.
 * <p>
 * All operations read the current tenant from {@link TenantContext} and scope
 * all repository queries by {@code clientId} to enforce strict tenant isolation.
 * Subjects belonging to other tenants are invisible — lookups return 404,
 * never leaking cross-tenant existence.
 *
 * @see com.perhac.permissio.security.ApiKeyAuthenticationFilter
 */
@Service
public class SubjectService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private final SubjectRepository subjectRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public SubjectService(SubjectRepository subjectRepository,
                          PasswordEncoder passwordEncoder,
                          ObjectMapper objectMapper) {
        this.subjectRepository = subjectRepository;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new Subject under the current tenant.
     *
     * @throws ConflictException if a Subject with the same externalId already exists for this tenant
     */
    public SubjectResponse createSubject(CreateSubjectRequest request) {
        UUID clientId = TenantContext.get();

        if (subjectRepository.existsByClientIdAndExternalId(clientId, request.getExternalId())) {
            throw new ConflictException(
                    "Subject with externalId '" + request.getExternalId() + "' already exists");
        }

        String attributesJson = serializeAttributes(request.getAttributes());
        String passwordHash = (request.getPassword() != null && !request.getPassword().isBlank())
                ? passwordEncoder.encode(request.getPassword())
                : "";

        Subject subject = Subject.builder()
                .clientId(clientId)
                .externalId(request.getExternalId())
                .passwordHash(passwordHash)
                .attributes(attributesJson)
                .createdAt(Instant.now())
                .build();

        subject = subjectRepository.save(subject);
        return toResponse(subject);
    }

    /**
     * Retrieves a Subject by its internal UUID, scoped to the current tenant.
     *
     * @throws NotFoundException if not found or belongs to another tenant
     */
    public SubjectResponse getSubjectById(UUID subjectId) {
        UUID clientId = TenantContext.get();
        Subject subject = subjectRepository.findByClientIdAndId(clientId, subjectId)
                .orElseThrow(() -> new NotFoundException("Subject not found"));
        return toResponse(subject);
    }

    /**
     * Retrieves a Subject by its external identifier, scoped to the current tenant.
     *
     * @throws NotFoundException if not found or belongs to another tenant
     */
    public SubjectResponse getSubjectByExternalId(String externalId) {
        UUID clientId = TenantContext.get();
        Subject subject = subjectRepository.findByClientIdAndExternalId(clientId, externalId)
                .orElseThrow(() -> new NotFoundException("Subject not found"));
        return toResponse(subject);
    }

    /**
     * Lists all Subjects belonging to the current tenant.
     */
    public List<SubjectResponse> listSubjects() {
        UUID clientId = TenantContext.get();
        return subjectRepository.findAllByClientId(clientId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Replaces the ABAC attributes for a Subject (full replacement, not merge).
     *
     * @throws NotFoundException if not found or belongs to another tenant
     */
    public SubjectResponse updateAttributes(UUID subjectId, Map<String, Object> newAttributes) {
        UUID clientId = TenantContext.get();
        Subject subject = subjectRepository.findByClientIdAndId(clientId, subjectId)
                .orElseThrow(() -> new NotFoundException("Subject not found"));

        subject.setAttributes(serializeAttributes(newAttributes));
        subject = subjectRepository.save(subject);
        return toResponse(subject);
    }

    /**
     * Deletes a Subject by its internal UUID, scoped to the current tenant.
     *
     * @throws NotFoundException if not found or belongs to another tenant
     */
    public void deleteSubject(UUID subjectId) {
        UUID clientId = TenantContext.get();
        Subject subject = subjectRepository.findByClientIdAndId(clientId, subjectId)
                .orElseThrow(() -> new NotFoundException("Subject not found"));
        subjectRepository.delete(subject);
    }

    // =========================================================================
    // Mapping Helpers
    // =========================================================================

    private SubjectResponse toResponse(Subject subject) {
        return new SubjectResponse(
                subject.getId(),
                subject.getClientId(),
                subject.getExternalId(),
                deserializeAttributes(subject.getAttributes()),
                subject.getCreatedAt()
        );
    }

    private String serializeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid attributes format", e);
        }
    }

    private Map<String, Object> deserializeAttributes(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE_REF);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize attributes", e);
        }
    }
}
