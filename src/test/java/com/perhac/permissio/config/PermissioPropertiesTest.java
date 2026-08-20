package com.perhac.permissio.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PermissioPropertiesTest {

    @Test
    @DisplayName("validate: passes in dev/test profile with valid secrets")
    void validate_devProfile_passes() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        PermissioProperties props = new PermissioProperties(env);
        props.getJwt().setSecret("test-profile-secret-key-32chars-long");
        props.getApiKey().setSalt("test-profile-salt");

        props.validate();

        assertThat(props.getJwt().getSecret()).isEqualTo("test-profile-secret-key-32chars-long");
        assertThat(props.getApiKey().getSalt()).isEqualTo("test-profile-salt");
        assertThat(props.getObservability().getOtel().isEnabled()).isTrue();
        assertThat(props.getObservability().getLogging().getConsole().isEnabled()).isTrue();
    }

    @Test
    @DisplayName("validate: throws if jwt secret is missing or too short")
    void validate_missingOrShortJwtSecret_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        PermissioProperties props = new PermissioProperties(env);
        props.getApiKey().setSalt("salt");

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permissio.jwt.secret is not set");

        props.getJwt().setSecret("short");
        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be at least 32 characters");
    }

    @Test
    @DisplayName("validate: throws if apiKey salt is missing")
    void validate_missingApiKeySalt_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("test");

        PermissioProperties props = new PermissioProperties(env);
        props.getJwt().setSecret("valid-secret-key-that-is-at-least-32-chars");

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("permissio.api-key.salt is not set");
    }

    @Test
    @DisplayName("validate: throws in prod if dev/test placeholder secrets are detected")
    void validate_prodProfileWithPlaceholder_throws() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");

        PermissioProperties props = new PermissioProperties(env);
        props.getJwt().setSecret("local-dev-secret-key-not-for-production-use-only-32chars");
        props.getApiKey().setSalt("valid-salt-value-for-production");

        assertThatThrownBy(props::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Detected dev/test placeholder secret/salt");
    }
}
