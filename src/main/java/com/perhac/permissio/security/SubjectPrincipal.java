package com.perhac.permissio.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;
import java.util.UUID;

/**
 * Authentication token placed into the {@link org.springframework.security.core.context.SecurityContext}
 * after a valid JWT is processed by {@link JwtAuthenticationFilter}.
 * <p>
 * Carries the Subject's UUID, Client (tenant) UUID, and external ID extracted from the JWT claims.
 */
public class SubjectPrincipal extends AbstractAuthenticationToken {

    private final UUID subjectId;
    private final UUID clientId;
    private final String externalId;

    public SubjectPrincipal(UUID subjectId, UUID clientId, String externalId) {
        super(Collections.emptyList());
        this.subjectId = subjectId;
        this.clientId = clientId;
        this.externalId = externalId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        // JWT credentials are not retained after validation
        return null;
    }

    @Override
    public Object getPrincipal() {
        return subjectId;
    }

    public UUID getSubjectId() {
        return subjectId;
    }

    public UUID getClientId() {
        return clientId;
    }

    public String getExternalId() {
        return externalId;
    }
}
