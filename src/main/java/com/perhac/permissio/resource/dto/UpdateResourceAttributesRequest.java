package com.perhac.permissio.resource.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request body for {@code PUT /api/v1/resources/{id}/attributes}.
 * <p>
 * Replaces the entire attributes map for the target Resource.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResourceAttributesRequest {

    @NotNull(message = "attributes must not be null")
    private Map<String, Object> attributes;
}
