package com.perhac.permissio.security;

import com.perhac.permissio.config.PermissioProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * Generates and validates JWT tokens using HMAC-SHA256.
 * <p>
 * Each token embeds:
 * <ul>
 *   <li>{@code sub} — the Subject UUID</li>
 *   <li>{@code clientId} — the Tenant/Client UUID</li>
 *   <li>{@code externalId} — the human-readable subject identifier</li>
 * </ul>
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtTokenProvider(PermissioProperties properties) {
        this.signingKey = Keys.hmacShaKeyFor(
                properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
        this.expirationMs = properties.getJwt().getExpirationMs();
    }

    /**
     * Generates a signed JWT embedding subject identity and tenant context.
     */
    public String generateToken(UUID subjectId, UUID clientId, String externalId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(subjectId.toString())
                .claim("clientId", clientId.toString())
                .claim("externalId", externalId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates the token's signature and expiration.
     *
     * @return {@code true} if the token is valid, {@code false} otherwise
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            parseClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.debug("JWT expired: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT invalid: {}", e.getMessage());
        }
        return false;
    }

    public UUID getSubjectIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).getSubject());
    }

    public UUID getClientIdFromToken(String token) {
        return UUID.fromString(parseClaims(token).get("clientId", String.class));
    }

    public String getExternalIdFromToken(String token) {
        return parseClaims(token).get("externalId", String.class);
    }

    /**
     * Returns the configured expiration duration in milliseconds.
     */
    public long getExpirationMs() {
        return expirationMs;
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
