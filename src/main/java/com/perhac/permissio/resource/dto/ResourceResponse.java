package com.perhac.permissio.resource.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for Resource CRUD operations.
 * <p>
 * Dynamic attributes are deserialized from JSON text into a typed map.
 */
public record ResourceResponse(
        UUID id,
        UUID clientId,
        String resourceType,
        String externalId,
        Map<String, Object> attributes,
        Instant createdAt
) {
}
