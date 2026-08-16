package com.perhac.permissio.subject.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request body for {@code POST /api/v1/subjects}.
 * <p>
 * Password is optional — not all subject provisioning flows require one
 * (e.g., machine-to-machine subjects). When present, it is hashed with BCrypt
 * before storage.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSubjectRequest {

    @NotBlank(message = "externalId is required")
    private String externalId;

    private String password;

    private Map<String, Object> attributes;
}
