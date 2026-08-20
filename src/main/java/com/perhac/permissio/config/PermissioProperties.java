package com.perhac.permissio.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Centralized, type-safe configuration properties for the entire {@code permissio.*} namespace.
 * <p>
 * Unifies JWT, API key security, OpenTelemetry, distributed tracing, metrics, and logging settings.
 */
@Configuration
@ConfigurationProperties(prefix = "permissio")
@Data
public class PermissioProperties {

    @NestedConfigurationProperty
    private Jwt jwt = new Jwt();

    @NestedConfigurationProperty
    private ApiKey apiKey = new ApiKey();

    @NestedConfigurationProperty
    private Observability observability = new Observability();

    private final Environment environment;

    public PermissioProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        boolean isDevOrTest = environment.matchesProfiles("dev", "test");

        if (jwt.getSecret() == null || jwt.getSecret().isBlank()) {
            throw new IllegalStateException(
                "permissio.jwt.secret is not set. Set PERMISSIO_JWT_SECRET env var."
            );
        }
        if (jwt.getSecret().length() < 32) {
            throw new IllegalStateException(
                "permissio.jwt.secret must be at least 32 characters for HMAC-SHA256."
            );
        }
        if (apiKey.getSalt() == null || apiKey.getSalt().isBlank()) {
            throw new IllegalStateException(
                "permissio.api-key.salt is not set. Set PERMISSIO_API_KEY_SALT env var."
            );
        }

        if (!isDevOrTest) {
            if (jwt.getSecret().contains("dev-secret-key")
                    || jwt.getSecret().contains("test-profile-secret")
                    || apiKey.getSalt().contains("dev-salt")
                    || apiKey.getSalt().contains("test-profile-salt")) {
                throw new IllegalStateException(
                    "Detected dev/test placeholder secret/salt while running outside dev/test profiles. "
                    + "Refusing to start."
                );
            }
        }
    }

    @Data
    public static class Jwt {
        /**
         * Secret key for signing and verifying JWT tokens (min 32 characters for HMAC-SHA256).
         * Must be supplied externally — no hardcoded default in non-dev environments.
         */
        private String secret;

        /**
         * JWT token expiration time in milliseconds (default: 15 minutes = 900,000 ms).
         */
        private long expirationMs = 900000;
    }

    @Data
    public static class ApiKey {
        /**
         * Salt used for hashing API keys before database storage.
         * Must be supplied externally — no hardcoded default in non-dev environments.
         */
        private String salt;
    }

    @Data
    public static class Observability {
        @NestedConfigurationProperty
        private Otel otel = new Otel();

        @NestedConfigurationProperty
        private Logging logging = new Logging();
    }

    @Data
    public static class Otel {
        /** Master switch for OpenTelemetry exporter registration */
        private boolean enabled = true;

        /** OTLP collector endpoint (e.g. http://localhost:4318) */
        private String endpoint = "http://localhost:4318";

        /** OTLP protocol (http/protobuf or grpc) */
        private String protocol = "http/protobuf";

        /** Trace sampling probability (0.0 to 1.0) */
        private double samplingProbability = 1.0;

        @NestedConfigurationProperty
        private Traces traces = new Traces();

        @NestedConfigurationProperty
        private Metrics metrics = new Metrics();

        @NestedConfigurationProperty
        private Logs logs = new Logs();
    }

    @Data
    public static class Traces {
        private boolean enabled = true;
    }

    @Data
    public static class Metrics {
        private boolean enabled = true;
    }

    @Data
    public static class Logs {
        /** Option in config whether to emit logs to OpenTelemetry OTLP exporter */
        private boolean enabled = false;
    }

    @Data
    public static class Logging {
        @NestedConfigurationProperty
        private Console console = new Console();
    }

    @Data
    public static class Console {
        /** Option to ensure console logging remains active */
        private boolean enabled = true;

        /** Whether console log should be formatted as structured JSON */
        private boolean structured = false;
    }
}