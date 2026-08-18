package com.perhac.permissio.authorization.dto;

import com.perhac.permissio.common.model.Action;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/authorize}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthorizeRequest {

    @NotNull(message = "subjectId is required")
    private UUID subjectId;

    @NotNull(message = "resourceId is required")
    private UUID resourceId;

    @NotNull(message = "action is required")
    private Action action;
}
