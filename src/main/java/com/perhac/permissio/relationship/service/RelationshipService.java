package com.perhac.permissio.relationship.service;

import com.perhac.permissio.common.exception.ConflictException;
import com.perhac.permissio.common.exception.NotFoundException;
import com.perhac.permissio.relationship.dto.CreateRelationshipRequest;
import com.perhac.permissio.relationship.dto.RelationshipResponse;
import com.perhac.permissio.relationship.entity.Relationship;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.security.TenantContext;
import com.perhac.permissio.subject.repository.SubjectRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Business logic for tenant-scoped Relationship (ReBAC tuple) CRUD operations.
 * <p>
 * Enforces:
 * <ul>
 *   <li>Relational integrity: Subject and Resource must belong to the current calling tenant.</li>
 *   <li>Duplicate tuple prevention: {@code (clientId, subjectId, resourceId, relation)} is unique.</li>
 *   <li>Strict tenant isolation: Entities of other tenants return 404 (Not Found).</li>
 * </ul>
 */
@Service
public class RelationshipService {

    private final RelationshipRepository relationshipRepository;
    private final SubjectRepository subjectRepository;
    private final ResourceRepository resourceRepository;

    public RelationshipService(RelationshipRepository relationshipRepository,
                               SubjectRepository subjectRepository,
                               ResourceRepository resourceRepository) {
        this.relationshipRepository = relationshipRepository;
        this.subjectRepository = subjectRepository;
        this.resourceRepository = resourceRepository;
    }

    /**
     * Creates a new relationship tuple under the current tenant.
     *
     * @throws NotFoundException if subject or resource does not exist under the tenant
     * @throws ConflictException if the relationship tuple already exists
     */
    public RelationshipResponse createRelationship(CreateRelationshipRequest request) {
        UUID clientId = TenantContext.get();

        // 1. Verify subject exists in current tenant
        subjectRepository.findByClientIdAndId(clientId, request.getSubjectId())
                .orElseThrow(() -> new NotFoundException("Subject not found"));

        // 2. Verify resource exists in current tenant
        resourceRepository.findByClientIdAndId(clientId, request.getResourceId())
                .orElseThrow(() -> new NotFoundException("Resource not found"));

        // 3. Prevent duplicate relationship tuples
        if (relationshipRepository.existsByClientIdAndSubjectIdAndResourceIdAndRelation(
                clientId, request.getSubjectId(), request.getResourceId(), request.getRelation())) {
            throw new ConflictException("Relationship tuple already exists");
        }

        // 4. Save and return
        Relationship relationship = Relationship.builder()
                .clientId(clientId)
                .subjectId(request.getSubjectId())
                .resourceId(request.getResourceId())
                .relation(request.getRelation())
                .createdAt(Instant.now())
                .build();

        relationship = relationshipRepository.save(relationship);
        return toResponse(relationship);
    }

    /**
     * Retrieves a relationship by ID, scoped to the current tenant.
     *
     * @throws NotFoundException if not found or belongs to another tenant
     */
    public RelationshipResponse getRelationshipById(UUID id) {
        UUID clientId = TenantContext.get();
        Relationship relationship = relationshipRepository.findByClientIdAndId(clientId, id)
                .orElseThrow(() -> new NotFoundException("Relationship not found"));
        return toResponse(relationship);
    }

    /**
     * Lists relationships for the current tenant, with optional subject and resource filters.
     */
    public List<RelationshipResponse> listRelationships(UUID subjectId, UUID resourceId) {
        UUID clientId = TenantContext.get();
        List<Relationship> relationships;

        if (subjectId != null && resourceId != null) {
            relationships = relationshipRepository.findByClientIdAndSubjectIdAndResourceId(
                    clientId, subjectId, resourceId);
        } else if (subjectId != null) {
            relationships = relationshipRepository.findByClientIdAndSubjectId(clientId, subjectId);
        } else if (resourceId != null) {
            relationships = relationshipRepository.findByClientIdAndResourceId(clientId, resourceId);
        } else {
            relationships = relationshipRepository.findAllByClientId(clientId);
        }

        return relationships.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Deletes a relationship by ID, scoped to the current tenant.
     *
     * @throws NotFoundException if not found or belongs to another tenant
     */
    public void deleteRelationship(UUID id) {
        UUID clientId = TenantContext.get();
        Relationship relationship = relationshipRepository.findByClientIdAndId(clientId, id)
                .orElseThrow(() -> new NotFoundException("Relationship not found"));
        relationshipRepository.delete(relationship);
    }

    private RelationshipResponse toResponse(Relationship relationship) {
        return new RelationshipResponse(
                relationship.getId(),
                relationship.getClientId(),
                relationship.getSubjectId(),
                relationship.getResourceId(),
                relationship.getRelation(),
                relationship.getCreatedAt()
        );
    }
}
