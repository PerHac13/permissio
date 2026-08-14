package com.perhac.permissio.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes API keys using SHA-256 with a configurable salt.
 * <p>
 * API keys are never stored in plaintext — only the salted hash is persisted
 * in the {@code clients.api_key_hash} column.
 */
public class ApiKeyHasher {

    private final String salt;

    public ApiKeyHasher(String salt) {
        this.salt = salt;
    }

    /**
     * Produces a deterministic SHA-256 hash of {@code rawApiKey + salt}.
     */
    public String hash(String rawApiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest((rawApiKey + salt).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Returns {@code true} if the raw key, when hashed, matches the stored hash.
     */
    public boolean matches(String rawApiKey, String storedHash) {
        return hash(rawApiKey).equals(storedHash);
    }
}
