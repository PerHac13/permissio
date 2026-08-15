package com.perhac.permissio.authentication.controller;

import com.perhac.permissio.authentication.dto.AuthResponse;
import com.perhac.permissio.authentication.dto.LoginRequest;
import com.perhac.permissio.authentication.dto.RegisterRequest;
import com.perhac.permissio.authentication.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for Subject authentication.
 * <p>
 * Both endpoints require a valid {@code X-API-Key} header to establish tenant context.
 * <ul>
 *   <li>{@code POST /api/v1/auth/register} — 201 Created + JWT</li>
 *   <li>{@code POST /api/v1/auth/login} — 200 OK + JWT</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }
}
