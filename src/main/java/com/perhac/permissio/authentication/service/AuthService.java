package com.perhac.permissio.authentication.service;

import com.perhac.permissio.authentication.dto.AuthResponse;
import com.perhac.permissio.authentication.dto.LoginRequest;
import com.perhac.permissio.authentication.dto.RegisterRequest;
import com.perhac.permissio.common.exception.ConflictException;
import com.perhac.permissio.common.exception.UnauthorizedException;
import com.perhac.permissio.security.JwtTokenProvider;
import com.perhac.permissio.security.TenantContext;
import com.perhac.permissio.subject.entity.Subject;
import com.perhac.permissio.subject.repository.SubjectRepository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Handles Subject registration and authentication within a tenant context.
 * <p>
 * Both {@code register} and {@code login} assume that {@link TenantContext}
 * has already been set by the {@link com.perhac.permissio.security.ApiKeyAuthenticationFilter}.
 */
@Service
public class AuthService {

    private final SubjectRepository subjectRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final ObjectMapper objectMapper;

    public AuthService(SubjectRepository subjectRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider jwtTokenProvider,
                       ObjectMapper objectMapper) {
        this.subjectRepository = subjectRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.objectMapper = objectMapper;
    }

    /**
     * Registers a new Subject under the current tenant.
     *
     * @throws ConflictException if a Subject with the same externalId already exists for this tenant
     */
    public AuthResponse register(RegisterRequest request) {
        UUID clientId = TenantContext.get();

        if (subjectRepository.existsByClientIdAndExternalId(clientId, request.getExternalId())) {
            throw new ConflictException(
                    "Subject with externalId '" + request.getExternalId() + "' already exists");
        }

        String attributesJson = serializeAttributes(request.getAttributes());

        Subject subject = Subject.builder()
                .clientId(clientId)
                .externalId(request.getExternalId())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .attributes(attributesJson)
                .createdAt(Instant.now())
                .build();

        subject = subjectRepository.save(subject);

        String token = jwtTokenProvider.generateToken(subject.getId(), clientId, subject.getExternalId());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .subjectId(subject.getId())
                .externalId(subject.getExternalId())
                .build();
    }

    /**
     * Authenticates a Subject by externalId and password, returning a signed JWT.
     *
     * @throws UnauthorizedException if credentials are invalid
     */
    public AuthResponse login(LoginRequest request) {
        UUID clientId = TenantContext.get();

        Subject subject = subjectRepository.findByClientIdAndExternalId(clientId, request.getExternalId())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), subject.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        String token = jwtTokenProvider.generateToken(subject.getId(), clientId, subject.getExternalId());

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(jwtTokenProvider.getExpirationMs())
                .subjectId(subject.getId())
                .externalId(subject.getExternalId())
                .build();
    }

    private String serializeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(attributes);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid attributes format", e);
        }
    }
}
