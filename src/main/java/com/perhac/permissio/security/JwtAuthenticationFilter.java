package com.perhac.permissio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Extracts and validates JWTs from the {@code Authorization: Bearer <token>} header.
 * <p>
 * On success, populates the {@link org.springframework.security.core.context.SecurityContext}
 * with a {@link SubjectPrincipal}. Also verifies that the JWT's {@code clientId} claim
 * matches the current {@link TenantContext} to prevent cross-tenant token replay attacks.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null) {
            if (!jwtTokenProvider.validateToken(token)) {
                sendUnauthorized(response, "Invalid or expired JWT token");
                return;
            }

            UUID tokenClientId = jwtTokenProvider.getClientIdFromToken(token);
            UUID tenantClientId = TenantContext.get();

            // Cross-tenant replay protection: JWT must belong to the same tenant
            if (tenantClientId != null && !tenantClientId.equals(tokenClientId)) {
                log.warn("Cross-tenant token replay detected: JWT clientId={} vs TenantContext={}",
                        tokenClientId, tenantClientId);
                sendUnauthorized(response, "Token does not belong to this tenant");
                return;
            }

            UUID subjectId = jwtTokenProvider.getSubjectIdFromToken(token);
            String externalId = jwtTokenProvider.getExternalIdFromToken(token);

            SubjectPrincipal principal = new SubjectPrincipal(subjectId, tokenClientId, externalId);
            SecurityContextHolder.getContext().setAuthentication(principal);
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Auth endpoints (register/login) don't require a JWT
        return path.startsWith("/api/v1/auth/")
                || path.startsWith("/actuator/")
                || path.startsWith("/h2-console")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\",\"traceId\":\"N/A\"}"
        );
    }
}
