package com.perhac.permissio.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link JwtAuthenticationFilter}.
 * <p>
 * Verifies valid token → SecurityContext, invalid/expired → 401,
 * missing token → pass-through, and cross-tenant token rejection.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(jwtTokenProvider);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void validToken_populatesSecurityContext() throws ServletException, IOException {
        UUID subjectId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        String externalId = "alice";
        String token = "valid.jwt.token";

        TenantContext.set(clientId);
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getSubjectIdFromToken(token)).thenReturn(subjectId);
        when(jwtTokenProvider.getClientIdFromToken(token)).thenReturn(clientId);
        when(jwtTokenProvider.getExternalIdFromToken(token)).thenReturn(externalId);

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isInstanceOf(SubjectPrincipal.class);

        SubjectPrincipal principal = (SubjectPrincipal) SecurityContextHolder.getContext().getAuthentication();
        assertThat(principal.getSubjectId()).isEqualTo(subjectId);
        assertThat(principal.getClientId()).isEqualTo(clientId);
        assertThat(principal.getExternalId()).isEqualTo(externalId);
        assertThat(principal.isAuthenticated()).isTrue();
    }

    @Test
    void invalidToken_returns401() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer invalid.token");

        when(jwtTokenProvider.validateToken("invalid.token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid or expired JWT token");
        verify(filterChain, never()).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void expiredToken_returns401() throws ServletException, IOException {
        request.addHeader("Authorization", "Bearer expired.jwt.token");

        when(jwtTokenProvider.validateToken("expired.jwt.token")).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void noAuthorizationHeader_passesThrough() throws ServletException, IOException {
        // No Authorization header — filter should pass through
        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void nonBearerAuthorizationHeader_passesThrough() throws ServletException, IOException {
        request.addHeader("Authorization", "Basic dXNlcjpwYXNz");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void crossTenantToken_returns401() throws ServletException, IOException {
        UUID tokenClientId = UUID.randomUUID();
        UUID tenantClientId = UUID.randomUUID();
        String token = "cross.tenant.token";

        // TenantContext set to a different tenant than the token's clientId
        TenantContext.set(tenantClientId);
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getClientIdFromToken(token)).thenReturn(tokenClientId);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Token does not belong to this tenant");
        verify(filterChain, never()).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void matchingTenantToken_succeeds() throws ServletException, IOException {
        UUID clientId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        String token = "matching.tenant.token";

        TenantContext.set(clientId);
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getSubjectIdFromToken(token)).thenReturn(subjectId);
        when(jwtTokenProvider.getClientIdFromToken(token)).thenReturn(clientId);
        when(jwtTokenProvider.getExternalIdFromToken(token)).thenReturn("alice");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    void validToken_withNullTenantContext_succeeds() throws ServletException, IOException {
        // Edge case: if TenantContext is null (shouldn't happen in normal flow),
        // the filter should still set the authentication
        UUID subjectId = UUID.randomUUID();
        UUID clientId = UUID.randomUUID();
        String token = "valid.jwt.token";

        // TenantContext is NOT set
        request.addHeader("Authorization", "Bearer " + token);

        when(jwtTokenProvider.validateToken(token)).thenReturn(true);
        when(jwtTokenProvider.getSubjectIdFromToken(token)).thenReturn(subjectId);
        when(jwtTokenProvider.getClientIdFromToken(token)).thenReturn(clientId);
        when(jwtTokenProvider.getExternalIdFromToken(token)).thenReturn("alice");

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    // =========================================================================
    // shouldNotFilter tests
    // =========================================================================

    @Test
    void authRegisterEndpoint_isSkipped() {
        request.setRequestURI("/api/v1/auth/register");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void authLoginEndpoint_isSkipped() {
        request.setRequestURI("/api/v1/auth/login");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void actuatorEndpoint_isSkipped() {
        request.setRequestURI("/actuator/health");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void h2ConsoleEndpoint_isSkipped() {
        request.setRequestURI("/h2-console/login.do");
        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void protectedEndpoint_isNotSkipped() {
        request.setRequestURI("/api/v1/authorize");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void protectedResourceEndpoint_isNotSkipped() {
        request.setRequestURI("/api/v1/resources");
        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
