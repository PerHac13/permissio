package com.perhac.permissio.policy.dto;

import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.policy.entity.PolicyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request payload for creating a new policy rule.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePolicyRequest {

    @NotBlank(message = "resourceType is required")
    private String resourceType;

    @NotNull(message = "action is required")
    private Action action;

    @NotNull(message = "policyType is required")
    private PolicyType policyType;

    @NotBlank(message = "expression is required")
    private String expression;
}
