package com.perhac.permissio.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — ApiKeyHasher: SHA-256 hashing utility for API keys.
 */
class ApiKeyHasherTest {

    private ApiKeyHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new ApiKeyHasher("test-salt");
    }

    @Test
    void hash_isNotEqualToPlaintext() {
        String rawKey = "my-secret-api-key";
        String hashed = hasher.hash(rawKey);
        assertThat(hashed).isNotEqualTo(rawKey);
    }

    @Test
    void hash_isDeterministic_sameInputProducesSameHash() {
        String rawKey = "my-secret-api-key";
        String hash1 = hasher.hash(rawKey);
        String hash2 = hasher.hash(rawKey);
        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    void matches_returnsTrueForCorrectKey() {
        String rawKey = "my-secret-api-key";
        String hashed = hasher.hash(rawKey);
        assertThat(hasher.matches(rawKey, hashed)).isTrue();
    }

    @Test
    void matches_returnsFalseForWrongKey() {
        String rawKey = "my-secret-api-key";
        String hashed = hasher.hash(rawKey);
        assertThat(hasher.matches("wrong-key", hashed)).isFalse();
    }

    @Test
    void hash_differentKeysProduceDifferentHashes() {
        String hash1 = hasher.hash("key-one");
        String hash2 = hasher.hash("key-two");
        assertThat(hash1).isNotEqualTo(hash2);
    }

    @Test
    void hash_differentSaltsProduceDifferentHashes() {
        ApiKeyHasher otherHasher = new ApiKeyHasher("different-salt");
        String rawKey = "my-secret-api-key";
        String hash1 = hasher.hash(rawKey);
        String hash2 = otherHasher.hash(rawKey);
        assertThat(hash1).isNotEqualTo(hash2);
    }
}
