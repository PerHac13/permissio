package com.perhac.permissio.authentication.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Response body returned by both register and login endpoints.
 * Contains the signed JWT and subject identity information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;

    @Builder.Default
    private String tokenType = "Bearer";

    private long expiresIn;

    private UUID subjectId;

    private String externalId;
}
