package com.perhac.permissio.security;

import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.service.ClientService;
import com.perhac.permissio.common.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Reads the {@code X-API-Key} header, resolves the client (tenant),
 * and sets {@link TenantContext} for the duration of the request.
 * <p>
 * Must always clear {@link TenantContext} in the {@code finally} block
 * to prevent tenant leakage across pooled threads.
 */
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-API-Key";

    private final ClientService clientService;

    public ApiKeyAuthenticationFilter(ClientService clientService) {
        this.clientService = clientService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.isBlank()) {
            sendUnauthorized(response, "Missing X-API-Key header");
            return;
        }

        try {
            Client client = clientService.resolveByApiKey(apiKey);
            TenantContext.set(client.getId());
            filterChain.doFilter(request, response);
        } catch (UnauthorizedException e) {
            sendUnauthorized(response, e.getMessage());
        } finally {
            TenantContext.clear();
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Auth endpoints (register/login) DO require X-API-Key for tenant context
        return path.startsWith("/actuator/")
                || path.startsWith("/h2-console");
    }

    private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(
                "{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\",\"traceId\":\"N/A\"}"
        );
    }
}
