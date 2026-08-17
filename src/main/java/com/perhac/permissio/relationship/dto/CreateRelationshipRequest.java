package com.perhac.permissio.relationship.dto;

import com.perhac.permissio.relationship.entity.Relation;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/relationships}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateRelationshipRequest {

    @NotNull(message = "subjectId is required")
    private UUID subjectId;

    @NotNull(message = "resourceId is required")
    private UUID resourceId;

    @NotNull(message = "relation is required")
    private Relation relation;
}
