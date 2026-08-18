package com.perhac.permissio.authorization.controller;

import com.perhac.permissio.authorization.dto.AuthorizeRequest;
import com.perhac.permissio.authorization.dto.AuthorizeResponse;
import com.perhac.permissio.authorization.engine.AuthorizationEngine;
import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.authorization.service.AuthorizationContextBuilder;
import com.perhac.permissio.security.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for the core authorization decision endpoint.
 * <p>
 * Evaluates access requests across ReBAC, ABAC, and Business Rule pipelines for the calling tenant.
 * <ul>
 *   <li>{@code POST /api/v1/authorize} — 200 OK with {@link AuthorizeResponse}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/authorize")
public class AuthorizationController {

    private final AuthorizationContextBuilder contextBuilder;
    private final AuthorizationEngine authorizationEngine;

    public AuthorizationController(
            AuthorizationContextBuilder contextBuilder,
            AuthorizationEngine authorizationEngine) {
        this.contextBuilder = contextBuilder;
        this.authorizationEngine = authorizationEngine;
    }

    /**
     * Evaluates whether the requested action is permitted for the subject on the target resource.
     *
     * @param request the authorization request payload
     * @return 200 OK with {@link AuthorizeResponse}
     */
    @PostMapping
    public ResponseEntity<AuthorizeResponse> authorize(@Valid @RequestBody AuthorizeRequest request) {
        UUID clientId = TenantContext.get();
        AuthorizationContext context = contextBuilder.build(clientId, request);
        Decision decision = authorizationEngine.authorize(context);

        AuthorizeResponse response = new AuthorizeResponse(
                decision.allowed(),
                decision.reason(),
                decision.evaluator()
        );
        return ResponseEntity.ok(response);
    }
}
