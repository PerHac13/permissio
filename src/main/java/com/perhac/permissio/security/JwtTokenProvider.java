package com.perhac.permissio.security;

import com.perhac.permissio.config.PermissioProperties;
import com.perhac.permissio.config.RsaKeyProvider;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;

/**
 * Generates and validates JWT tokens using <strong>RS256</strong> (RSA + SHA-256).
 * <p>
 * Asymmetric signing means future services can verify tokens using only the public key,
 * without holding the private signing key (TRD Section 8).
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

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final long expirationMs;

    public JwtTokenProvider(RsaKeyProvider rsaKeyProvider, PermissioProperties properties) {
        this.privateKey = rsaKeyProvider.getPrivateKey();
        this.publicKey = rsaKeyProvider.getPublicKey();
        this.expirationMs = properties.getJwt().getExpirationMs();
    }

    /**
     * Generates an RS256-signed JWT embedding subject identity and tenant context.
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
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Validates the token's RS256 signature and expiration.
     * <p>
     * The parser is configured to require RS256 algorithm, which prevents
     * algorithm confusion attacks (e.g., an attacker switching to HS256
     * and using the public key as an HMAC secret).
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
                .verifyWith(publicKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
