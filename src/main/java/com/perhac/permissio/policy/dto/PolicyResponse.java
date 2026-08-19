package com.perhac.permissio.policy.dto;

import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.policy.entity.Policy;
import com.perhac.permissio.policy.entity.PolicyType;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for Policy operations.
 */
public record PolicyResponse(
        UUID id,
        UUID clientId,
        String resourceType,
        Action action,
        PolicyType policyType,
        String expression,
        Instant createdAt
) {
    public static PolicyResponse fromEntity(Policy policy) {
        return new PolicyResponse(
                policy.getId(),
                policy.getClientId(),
                policy.getResourceType(),
                policy.getAction(),
                policy.getPolicyType(),
                policy.getExpression(),
                policy.getCreatedAt()
        );
    }
}
