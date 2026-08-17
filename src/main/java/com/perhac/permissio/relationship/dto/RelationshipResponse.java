package com.perhac.permissio.relationship.dto;

import com.perhac.permissio.relationship.entity.Relation;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for Relationship CRUD operations.
 */
public record RelationshipResponse(
        UUID id,
        UUID clientId,
        UUID subjectId,
        UUID resourceId,
        Relation relation,
        Instant createdAt
) {
}
