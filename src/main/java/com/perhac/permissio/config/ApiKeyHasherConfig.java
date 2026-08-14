package com.perhac.permissio.config;

import com.perhac.permissio.security.ApiKeyHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures the {@link ApiKeyHasher} bean with the salt from application properties.
 */
@Configuration
public class ApiKeyHasherConfig {

    @Bean
    public ApiKeyHasher apiKeyHasher(PermissioProperties properties) {
        return new ApiKeyHasher(properties.getApiKey().getSalt());
    }
}
