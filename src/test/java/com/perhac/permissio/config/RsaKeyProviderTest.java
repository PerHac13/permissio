package com.perhac.permissio.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RsaKeyProvider Unit Tests")
class RsaKeyProviderTest {

    private KeyPair generateRsaKeyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    @Nested
    @DisplayName("Dev / Test Profile Behavior")
    class DevAndTestProfileTests {

        @Test
        @DisplayName("Auto-generates transient key pair when no keys configured in test profile")
        void autoGeneratesKeys_whenNoKeysConfiguredInTestProfile() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("test");

            PermissioProperties props = new PermissioProperties();

            RsaKeyProvider provider = new RsaKeyProvider(props, env);

            assertThat(provider.getPrivateKey()).isNotNull();
            assertThat(provider.getPublicKey()).isNotNull();
            assertThat(provider.getPrivateKey().getAlgorithm()).isEqualTo("RSA");
            assertThat(provider.getPublicKey().getAlgorithm()).isEqualTo("RSA");
        }

        @Test
        @DisplayName("Auto-generates transient key pair when no keys configured in dev profile")
        void autoGeneratesKeys_whenNoKeysConfiguredInDevProfile() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("dev");

            PermissioProperties props = new PermissioProperties();

            RsaKeyProvider provider = new RsaKeyProvider(props, env);

            assertThat(provider.getPrivateKey()).isNotNull();
            assertThat(provider.getPublicKey()).isNotNull();
        }
    }

    @Nested
    @DisplayName("Production Profile & Explicit Key Decoding")
    class ExplicitKeyDecodingTests {

        @Test
        @DisplayName("Decodes pure Base64 DER encoded keys correctly")
        void decodesPureBase64DerKeys() throws Exception {
            KeyPair keyPair = generateRsaKeyPair();
            String privBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            String pubBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod");

            PermissioProperties props = new PermissioProperties();
            props.getJwt().setPrivateKey(privBase64);
            props.getJwt().setPublicKey(pubBase64);

            RsaKeyProvider provider = new RsaKeyProvider(props, env);

            assertThat(provider.getPrivateKey()).isNotNull();
            assertThat(provider.getPublicKey()).isNotNull();
            assertThat(provider.getPrivateKey().getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
            assertThat(provider.getPublicKey().getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        }

        @Test
        @DisplayName("Decodes raw PEM strings with headers and newlines correctly")
        void decodesRawPemWithHeaders() throws Exception {
            KeyPair keyPair = generateRsaKeyPair();
            String privBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            String pubBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

            String rawPrivPem = "-----BEGIN PRIVATE KEY-----\n" + privBase64 + "\n-----END PRIVATE KEY-----";
            String rawPubPem = "-----BEGIN PUBLIC KEY-----\n" + pubBase64 + "\n-----END PUBLIC KEY-----";

            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod");

            PermissioProperties props = new PermissioProperties();
            props.getJwt().setPrivateKey(rawPrivPem);
            props.getJwt().setPublicKey(rawPubPem);

            RsaKeyProvider provider = new RsaKeyProvider(props, env);

            assertThat(provider.getPrivateKey()).isNotNull();
            assertThat(provider.getPublicKey()).isNotNull();
            assertThat(provider.getPrivateKey().getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
            assertThat(provider.getPublicKey().getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        }

        @Test
        @DisplayName("Decodes Base64-wrapped PEM files (double encoded) correctly")
        void decodesBase64WrappedPem() throws Exception {
            KeyPair keyPair = generateRsaKeyPair();
            String privBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
            String pubBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());

            String rawPrivPem = "-----BEGIN PRIVATE KEY-----\n" + privBase64 + "\n-----END PRIVATE KEY-----";
            String rawPubPem = "-----BEGIN PUBLIC KEY-----\n" + pubBase64 + "\n-----END PUBLIC KEY-----";

            String wrappedPriv = Base64.getEncoder().encodeToString(rawPrivPem.getBytes());
            String wrappedPub = Base64.getEncoder().encodeToString(rawPubPem.getBytes());

            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod");

            PermissioProperties props = new PermissioProperties();
            props.getJwt().setPrivateKey(wrappedPriv);
            props.getJwt().setPublicKey(wrappedPub);

            RsaKeyProvider provider = new RsaKeyProvider(props, env);

            assertThat(provider.getPrivateKey()).isNotNull();
            assertThat(provider.getPublicKey()).isNotNull();
            assertThat(provider.getPrivateKey().getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
            assertThat(provider.getPublicKey().getEncoded()).isEqualTo(keyPair.getPublic().getEncoded());
        }

        @Test
        @DisplayName("Throws IllegalStateException in prod profile when keys are missing")
        void throwsException_whenKeysMissingInProd() {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("prod");

            PermissioProperties props = new PermissioProperties();

            assertThatThrownBy(() -> new RsaKeyProvider(props, env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("permissio.jwt.private-key and permissio.jwt.public-key must be set");
        }
    }
}
