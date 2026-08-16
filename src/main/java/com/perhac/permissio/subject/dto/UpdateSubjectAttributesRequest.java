package com.perhac.permissio.subject.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request body for {@code PUT /api/v1/subjects/{id}/attributes}.
 * <p>
 * Replaces the entire attributes map for the target Subject.
 * Partial/merge updates are not supported in the current version.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSubjectAttributesRequest {

    @NotNull(message = "attributes must not be null")
    private Map<String, Object> attributes;
}
