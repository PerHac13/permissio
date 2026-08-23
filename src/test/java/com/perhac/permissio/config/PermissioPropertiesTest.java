package com.perhac.permissio.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissioPropertiesTest {

    @Test
    @DisplayName("validate: passes in dev/test profile with auto-generated RSA keys")
    void validate_devProfile_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        PermissioProperties props = new PermissioProperties(env);
        props.getApiKey().setSalt("test-profile-salt");

        props.validate();

        assertThat(props.getApiKey().getSalt()).isEqualTo("test-profile-salt");
        assertThat(props.getObservability().getOtel().isEnabled()).isTrue();
        assertThat(props.getObservability().getLogging().getConsole().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("validate: throws in prod if RSA keys are missing")
    void validate_prodProfileMissingRsaKeys_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        PermissioProperties props = new PermissioProperties(env);
        props.getApiKey().setSalt("valid-salt-value-for-production");

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permissio.jwt.private-key and permissio.jwt.public-key must be set");
    }

    @Test
    @DisplayName("validate: throws if apiKey salt is missing")
    void validate_missingApiKeySalt_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        PermissioProperties props = new PermissioProperties(env);

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permissio.api-key.salt is not set");
    }

    @Test
    @DisplayName("validate: throws in prod if dev/test placeholder salt is detected")
    void validate_prodProfileWithPlaceholder_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        PermissioProperties props = new PermissioProperties(env);
        props.getJwt().setPrivateKey("pem-private-key");
        props.getJwt().setPublicKey("pem-public-key");
        props.getApiKey().setSalt("dev-salt-value");

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Detected dev/test placeholder salt");
    }
}
