package com.perhac.permissio.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Provides RSA key pair for JWT signing (RS256).
 * <p>
 * In <strong>dev</strong> and <strong>test</strong> profiles, if no explicit keys are configured,
 * a transient 2048-bit RSA key pair is generated at startup. This ensures zero-config local
 * development while enforcing that production deployments must supply real keys.
 * <p>
 * In <strong>prod</strong> (or any non-dev/non-test profile), both
 * {@code permissio.jwt.private-key} and {@code permissio.jwt.public-key} must be set
 * as PEM-encoded Base64 strings (without headers/footers).
 */
@Component
public class RsaKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

    private final RSAPrivateKey privateKey;
    private final RSAPublicKey publicKey;

    public RsaKeyProvider(PermissioProperties properties, Environment environment) {
        boolean isDevOrTest = environment.matchesProfiles("dev", "test");
        String privateKeyPem = properties.getJwt().getPrivateKey();
        String publicKeyPem = properties.getJwt().getPublicKey();

        if (hasKey(privateKeyPem) && hasKey(publicKeyPem)) {
            // Explicit keys provided — decode PEM
            log.info("Loading RSA key pair from configuration properties");
            this.privateKey = decodePrivateKey(privateKeyPem);
            this.publicKey = decodePublicKey(publicKeyPem);
        } else if (isDevOrTest) {
            // Dev/test: auto-generate transient key pair
            log.warn("No RSA keys configured — generating transient 2048-bit key pair (dev/test only)");
            KeyPair keyPair = generateKeyPair();
            this.privateKey = (RSAPrivateKey) keyPair.getPrivate();
            this.publicKey = (RSAPublicKey) keyPair.getPublic();
        } else {
            throw new IllegalStateException(
                    "permissio.jwt.private-key and permissio.jwt.public-key must be set in non-dev/test profiles. "
                    + "Generate an RSA key pair and provide PEM-encoded Base64 strings."
            );
        }
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    private boolean hasKey(String key) {
        return key != null && !key.isBlank();
    }

    private static RSAPrivateKey decodePrivateKey(String pem) {
        try {
            String cleaned = stripPemHeaders(pem);
            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPrivateKey) factory.generatePrivate(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to decode RSA private key from PEM", e);
        }
    }

    private static RSAPublicKey decodePublicKey(String pem) {
        try {
            String cleaned = stripPemHeaders(pem);
            byte[] keyBytes = Base64.getDecoder().decode(cleaned);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) factory.generatePublic(spec);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to decode RSA public key from PEM", e);
        }
    }

    /**
     * Strips PEM header/footer lines and whitespace so the remaining string is pure Base64.
     */
    private static String stripPemHeaders(String pem) {
        return pem
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("RSA algorithm not available", e);
        }
    }
}
