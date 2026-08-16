package com.perhac.permissio.resource.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request body for {@code POST /api/v1/resources}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateResourceRequest {

    @NotBlank(message = "resourceType is required")
    private String resourceType;

    @NotBlank(message = "externalId is required")
    private String externalId;

    private Map<String, Object> attributes;
}
