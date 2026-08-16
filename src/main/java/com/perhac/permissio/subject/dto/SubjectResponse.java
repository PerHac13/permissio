package com.perhac.permissio.subject.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Response DTO for Subject CRUD operations.
 * <p>
 * Attributes are deserialized from the stored JSON string into a typed map
 * so callers receive structured data rather than raw JSON.
 */
public record SubjectResponse(
        UUID id,
        UUID clientId,
        String externalId,
        Map<String, Object> attributes,
        Instant createdAt
) {
}
