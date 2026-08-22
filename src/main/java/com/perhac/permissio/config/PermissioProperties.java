package com.perhac.permissio.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired(required = false)
    private Environment environment;

    public PermissioProperties() {
    }

    public PermissioProperties(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        boolean isDevOrTest = environment.matchesProfiles("dev", "test");

        // RS256 key validation: required in non-dev/test unless auto-generated
        boolean hasRsaKeys = hasValue(jwt.getPrivateKey()) && hasValue(jwt.getPublicKey());
        if (!isDevOrTest && !hasRsaKeys) {
            throw new IllegalStateException(
                "permissio.jwt.private-key and permissio.jwt.public-key must be set in non-dev/test profiles. "
                + "Set PERMISSIO_JWT_PRIVATE_KEY and PERMISSIO_JWT_PUBLIC_KEY env vars with PEM-encoded RSA keys."
            );
        }

        if (apiKey.getSalt() == null || apiKey.getSalt().isBlank()) {
            throw new IllegalStateException(
                "permissio.api-key.salt is not set. Set PERMISSIO_API_KEY_SALT env var."
            );
        }

        if (!isDevOrTest) {
            if (apiKey.getSalt().contains("dev-salt")
                    || apiKey.getSalt().contains("test-profile-salt")) {
                throw new IllegalStateException(
                    "Detected dev/test placeholder salt while running outside dev/test profiles. "
                    + "Refusing to start."
                );
            }
        }
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    @Data
    public static class Jwt {
        /**
         * @deprecated Use {@code privateKey}/{@code publicKey} for RS256 signing.
         * Retained for backward-compatible configuration parsing only.
         */
        @Deprecated(since = "1.0", forRemoval = true)
        private String secret;

        /**
         * PEM-encoded RSA private key (PKCS#8 format) for signing JWTs with RS256.
         * Can include or omit PEM header/footer lines.
         * In dev/test profiles, omitting this causes a transient key pair to be auto-generated.
         */
        private String privateKey;

        /**
         * PEM-encoded RSA public key (X.509 format) for verifying JWTs with RS256.
         * Can include or omit PEM header/footer lines.
         * In dev/test profiles, omitting this causes a transient key pair to be auto-generated.
         */
        private String publicKey;

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