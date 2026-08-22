package com.perhac.permissio.security;

import com.perhac.permissio.config.PermissioProperties;
import com.perhac.permissio.config.RsaKeyProvider;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtTokenProvider} with RS256 (asymmetric) signing.
 * <p>
 * Verifies token generation, validation, claim extraction,
 * expired token rejection, tampered token rejection, and
 * <strong>algorithm confusion attack rejection</strong> (HS256 token must be rejected).
 */
class JwtTokenProviderTest {

    private static final long EXPIRATION_MS = 900000; // 15 minutes

    private JwtTokenProvider tokenProvider;
    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPair keyPair = generateKeyPair();
        privateKey = (RSAPrivateKey) keyPair.getPrivate();
        publicKey = (RSAPublicKey) keyPair.getPublic();

        RsaKeyProvider rsaKeyProvider = mock(RsaKeyProvider.class);
        when(rsaKeyProvider.getPrivateKey()).thenReturn(privateKey);
        when(rsaKeyProvider.getPublicKey()).thenReturn(publicKey);

        PermissioProperties properties = mock(PermissioProperties.class);
        PermissioProperties.Jwt jwtProps = mock(PermissioProperties.Jwt.class);
        when(properties.getJwt()).thenReturn(jwtProps);
        when(jwtProps.getExpirationMs()).thenReturn(EXPIRATION_MS);

        tokenProvider = new JwtTokenProvider(rsaKeyProvider, properties);
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
    void validateToken_expiredToken_returnsFalse() throws Exception {
        // Create a provider with 0ms expiration
        RsaKeyProvider rsaKeyProvider = mock(RsaKeyProvider.class);
        when(rsaKeyProvider.getPrivateKey()).thenReturn(privateKey);
        when(rsaKeyProvider.getPublicKey()).thenReturn(publicKey);

        PermissioProperties properties = mock(PermissioProperties.class);
        PermissioProperties.Jwt jwtProps = mock(PermissioProperties.Jwt.class);
        when(properties.getJwt()).thenReturn(jwtProps);
        when(jwtProps.getExpirationMs()).thenReturn(0L);

        JwtTokenProvider expiredProvider = new JwtTokenProvider(rsaKeyProvider, properties);
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
    void validateToken_nullToken_returnsFalse() {
        assertThat(tokenProvider.validateToken(null)).isFalse();
    }

    @Test
    void validateToken_differentKeyPair_returnsFalse() throws Exception {
        // Generate token with one key pair, validate with another
        KeyPair otherKeyPair = generateKeyPair();
        RSAPrivateKey otherPrivateKey = (RSAPrivateKey) otherKeyPair.getPrivate();
        RSAPublicKey otherPublicKey = (RSAPublicKey) otherKeyPair.getPublic();

        RsaKeyProvider otherRsaKeyProvider = mock(RsaKeyProvider.class);
        when(otherRsaKeyProvider.getPrivateKey()).thenReturn(otherPrivateKey);
        when(otherRsaKeyProvider.getPublicKey()).thenReturn(otherPublicKey);

        PermissioProperties otherProps = mock(PermissioProperties.class);
        PermissioProperties.Jwt otherJwt = mock(PermissioProperties.Jwt.class);
        when(otherProps.getJwt()).thenReturn(otherJwt);
        when(otherJwt.getExpirationMs()).thenReturn(EXPIRATION_MS);

        JwtTokenProvider otherProvider = new JwtTokenProvider(otherRsaKeyProvider, otherProps);
        String token = otherProvider.generateToken(
                UUID.randomUUID(), UUID.randomUUID(), "alice");

        // Original provider should reject token signed with different key
        assertThat(tokenProvider.validateToken(token)).isFalse();
    }

    /**
     * Ticket 10.1 — Algorithm confusion attack prevention.
     * An HS256-signed token (using the public key bytes as HMAC secret)
     * MUST be rejected by the RS256 parser.
     */
    @Test
    void validateToken_hs256SignedToken_isRejected() {
        UUID subjectId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();

        // Attacker crafts an HS256 token using the public key bytes as HMAC secret
        SecretKey hmacKey = Keys.hmacShaKeyFor(
                publicKey.getEncoded().length >= 32
                        ? publicKey.getEncoded()
                        : "a]very-long-secret-key-for-hs256-at-least-32-chars!!".getBytes(StandardCharsets.UTF_8)
        );

        String hs256Token = Jwts.builder()
                .subject(subjectId.toString())
                .claim("clientId", clientId.toString())
                .claim("externalId", "attacker")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900000))
                .signWith(hmacKey)
                .compact();

        // RS256 parser must reject this HS256-signed token
        assertThat(tokenProvider.validateToken(hs256Token)).isFalse();
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

    private static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
