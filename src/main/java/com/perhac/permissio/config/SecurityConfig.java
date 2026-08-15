package com.perhac.permissio.config;

import com.perhac.permissio.security.ApiKeyAuthenticationFilter;
import com.perhac.permissio.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security configuration for Permissio.
 * <p>
 * - Stateless (no HTTP sessions — all auth via JWT + API key per request)
 * - CSRF disabled (stateless REST API)
 * - Public: actuator health, H2 console
 * - Auth endpoints require API key but no JWT
 * - All other endpoints require both API key and valid JWT
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
                          JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .frameOptions(frame -> frame.sameOrigin())) // Allow H2 console iframes
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/actuator/health",
                                "/h2-console/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(apiKeyAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(jwtAuthenticationFilter,
                        ApiKeyAuthenticationFilter.class);

        return http.build();
    }
}
