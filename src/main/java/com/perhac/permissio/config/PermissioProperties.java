package com.perhac.permissio.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Type-safe configuration properties for the {@code permissio.*} namespace.
 *
 * NOTE: No default secret/salt values are provided here. They MUST be supplied
 * via application-dev.yml (local dev or test) or environment variables in all
 * other environments. Startup will fail otherwise — see {@link #validate()}.
 */
@Configuration
@ConfigurationProperties(prefix = "permissio")
@Getter
@Setter
public class PermissioProperties {

    private final Jwt jwt = new Jwt();
    private final ApiKey apiKey = new ApiKey();

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

    @Getter
    @Setter
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

    @Getter
    @Setter
    public static class ApiKey {
        /**
         * Salt used for hashing API keys before database storage.
         * Must be supplied externally — no hardcoded default in non-dev environments.
         */
        private String salt;
    }
}