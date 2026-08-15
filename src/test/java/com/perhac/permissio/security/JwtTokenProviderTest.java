package com.perhac.permissio.security;

import com.perhac.permissio.config.PermissioProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtTokenProvider}.
 * <p>
 * Verifies token generation, validation, claim extraction,
 * expired token rejection, and tampered token rejection.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "test-profile-secret-key-not-for-production-use-32chars";
    private static final long EXPIRATION_MS = 900000; // 15 minutes

    private JwtTokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        PermissioProperties properties = mock(PermissioProperties.class);
        PermissioProperties.Jwt jwtProps = mock(PermissioProperties.Jwt.class);
        when(properties.getJwt()).thenReturn(jwtProps);
        when(jwtProps.getSecret()).thenReturn(SECRET);
        when(jwtProps.getExpirationMs()).thenReturn(EXPIRATION_MS);

        tokenProvider = new JwtTokenProvider(properties);
    }

    @Test
    void generateToken_returnsNonNullCompactJwt() {
        UUID subjectId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        String externalId = "alice";

        String token = tokenProvider.generateToken(subjectId, clientId, externalId);

        assertThat(token).isNotNull().isNotBlank();
        // JWT format: header.payload.signature
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = tokenProvider.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), "alice");

        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        String token = tokenProvider.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), "alice");

        // Tamper with the signature
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";

        assertThat(tokenProvider.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_expiredToken_returnsFalse() {
        // Create a provider with 0ms expiration
        PermissioProperties properties = mock(PermissioProperties.class);
        PermissioProperties.Jwt jwtProps = mock(PermissioProperties.Jwt.class);
        when(properties.getJwt()).thenReturn(jwtProps);
        when(jwtProps.getSecret()).thenReturn(SECRET);
        when(jwtProps.getExpirationMs()).thenReturn(0L);

        JwtTokenProvider expiredProvider = new JwtTokenProvider(properties);
        String token = expiredProvider.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), "alice");

        // Token is already expired (0ms TTL)
        assertThat(expiredProvider.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_malformedToken_returnsFalse() {
        assertThat(tokenProvider.validateToken("not.a.jwt")).isFalse();
    }

    @Test
    void validateToken_emptyString_returnsFalse() {
        assertThat(tokenProvider.validateToken("")).isFalse();
    }

    @Test
    void validateToken_differentSecret_returnsFalse() {
        // Generate with one secret, validate with another
        PermissioProperties otherProps = mock(PermissioProperties.class);
        PermissioProperties.Jwt otherJwt = mock(PermissioProperties.Jwt.class);
        when(otherProps.getJwt()).thenReturn(otherJwt);
        when(otherJwt.getSecret()).thenReturn("different-secret-key-that-is-at-least-32-chars");
        when(otherJwt.getExpirationMs()).thenReturn(EXPIRATION_MS);

        JwtTokenProvider otherProvider = new JwtTokenProvider(otherProps);
        String token = otherProvider.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), "alice");

        // Original provider should reject token signed with different key
        assertThat(tokenProvider.validateToken(token)).isFalse();
    }

    @Test
    void getSubjectIdFromToken_extractsCorrectId() {
        UUID subjectId = UUID.randomUUID();
        String token = tokenProvider.generateToken(subjectId, UUID.randomUUID(), "alice");

        assertThat(tokenProvider.getSubjectIdFromToken(token)).isEqualTo(subjectId);
    }

    @Test
    void getClientIdFromToken_extractsCorrectId() {
        UUID clientId = UUID.randomUUID();
        String token = tokenProvider.generateToken(UUID.randomUUID(), clientId, "alice");

        assertThat(tokenProvider.getClientIdFromToken(token)).isEqualTo(clientId);
    }

    @Test
    void getExternalIdFromToken_extractsCorrectValue() {
        String token = tokenProvider.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), "bob@example.com");

        assertThat(tokenProvider.getExternalIdFromToken(token)).isEqualTo("bob@example.com");
    }

    @Test
    void allClaims_roundTrip() {
        UUID subjectId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        String externalId = "user-42";

        String token = tokenProvider.generateToken(subjectId, clientId, externalId);

        assertThat(tokenProvider.getSubjectIdFromToken(token)).isEqualTo(subjectId);
        assertThat(tokenProvider.getClientIdFromToken(token)).isEqualTo(clientId);
        assertThat(tokenProvider.getExternalIdFromToken(token)).isEqualTo(externalId);
    }

    @Test
    void getExpirationMs_returnsConfiguredValue() {
        assertThat(tokenProvider.getExpirationMs()).isEqualTo(EXPIRATION_MS);
    }

    @Test
    void differentSubjects_produceDifferentTokens() {
        UUID clientId = UUID.randomUUID();
        String token1 = tokenProvider.generateToken(UUID.randomUUID(), clientId, "alice");
        String token2 = tokenProvider.generateToken(UUID.randomUUID(), clientId, "bob");

        assertThat(token1).isNotEqualTo(token2);
    }
}
