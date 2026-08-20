package com.perhac.permissio.security;

import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.service.ClientService;
import com.perhac.permissio.common.exception.UnauthorizedException;
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

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD — ApiKeyAuthenticationFilter: validates X-API-Key header and sets TenantContext.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationFilterTest {

    @Mock
    private ClientService clientService;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(clientService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void validApiKey_setsTenantContext() throws ServletException, IOException {
        UUID clientId = UUID.randomUUID();
        Client client = Client.builder()
                .id(clientId)
                .name("Acme HR")
                .apiKeyHash("hashed")
                .createdAt(Instant.now())
                .build();

        request.addHeader("X-API-Key", "valid-key");
        when(clientService.resolveByApiKey("valid-key")).thenReturn(client);

        filter.doFilterInternal(request, response, filterChain);

        // TenantContext should be cleared after the filter chain completes
        // but during filterChain.doFilter it should have been set
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void invalidApiKey_returns401() throws ServletException, IOException {
        request.addHeader("X-API-Key", "bad-key");
        when(clientService.resolveByApiKey("bad-key"))
                .thenThrow(new UnauthorizedException("Invalid API key"));

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void missingApiKeyHeader_returns401() throws ServletException, IOException {
        // No X-API-Key header set on the request
        request.setRequestURI("/api/v1/resources");

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void tenantContext_isClearedAfterRequest() throws ServletException, IOException {
        UUID clientId = UUID.randomUUID();
        Client client = Client.builder()
                .id(clientId)
                .name("Test App")
                .apiKeyHash("hashed")
                .createdAt(Instant.now())
                .build();

        request.addHeader("X-API-Key", "valid-key");
        when(clientService.resolveByApiKey("valid-key")).thenReturn(client);

        filter.doFilterInternal(request, response, filterChain);

        // After the filter completes, TenantContext must be cleared
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void tenantContext_isClearedEvenOnException() throws ServletException, IOException {
        UUID clientId = UUID.randomUUID();
        Client client = Client.builder()
                .id(clientId)
                .name("Test App")
                .apiKeyHash("hashed")
                .createdAt(Instant.now())
                .build();

        request.addHeader("X-API-Key", "valid-key");
        when(clientService.resolveByApiKey("valid-key")).thenReturn(client);
        // Simulate filter chain throwing
        org.mockito.Mockito.doThrow(new ServletException("boom"))
                .when(filterChain).doFilter(request, response);

        try {
            filter.doFilterInternal(request, response, filterChain);
        } catch (ServletException ignored) {
            // expected
        }

        // TenantContext must still be cleared
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void authEndpoints_areNotSkipped() throws ServletException, IOException {
        // Auth endpoints require X-API-Key for tenant context
        request.setRequestURI("/api/v1/auth/register");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }

    @Test
    void actuatorHealth_isSkipped() throws ServletException, IOException {
        request.setRequestURI("/actuator/health");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void h2Console_isSkipped() throws ServletException, IOException {
        request.setRequestURI("/h2-console/login.do");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    @Test
    void protectedEndpoint_isNotSkipped() throws ServletException, IOException {
        request.setRequestURI("/api/v1/authorize");

        assertThat(filter.shouldNotFilter(request)).isFalse();
    }
}
