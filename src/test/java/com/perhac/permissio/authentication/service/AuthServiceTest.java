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
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService} using Mockito.
 * <p>
 * Verifies registration success/failure, login success/failure,
 * password hashing, JWT issuance, and tenant scoping.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private AuthService authService;

    private final UUID clientId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        authService = new AuthService(subjectRepository, passwordEncoder, jwtTokenProvider, objectMapper);
        TenantContext.set(clientId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // Registration Tests
    // =========================================================================

    @Test
    void register_success_returnsAuthResponseWith201Fields() {
        RegisterRequest request = RegisterRequest.builder()
                .externalId("alice")
                .password("secret123")
                .build();

        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.existsByClientIdAndExternalId(clientId, "alice")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$hashed");
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject s = invocation.getArgument(0);
            s.setId(subjectId);
            return s;
        });
        when(jwtTokenProvider.generateToken(subjectId, clientId, "alice")).thenReturn("jwt-token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(900000L);

        AuthResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900000L);
        assertThat(response.getSubjectId()).isEqualTo(subjectId);
        assertThat(response.getExternalId()).isEqualTo("alice");
    }

    @Test
    void register_hashesPasswordBeforePersisting() {
        RegisterRequest request = RegisterRequest.builder()
                .externalId("alice")
                .password("plaintext")
                .build();

        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.existsByClientIdAndExternalId(clientId, "alice")).thenReturn(false);
        when(passwordEncoder.encode("plaintext")).thenReturn("$2a$encoded");
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject s = invocation.getArgument(0);
            s.setId(subjectId);
            return s;
        });
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(900000L);

        authService.register(request);

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$encoded");
        // Must never store plaintext
        assertThat(captor.getValue().getPasswordHash()).isNotEqualTo("plaintext");
    }

    @Test
    void register_setsClientIdFromTenantContext() {
        RegisterRequest request = RegisterRequest.builder()
                .externalId("alice")
                .password("secret123")
                .build();

        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.existsByClientIdAndExternalId(clientId, "alice")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject s = invocation.getArgument(0);
            s.setId(subjectId);
            return s;
        });
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(900000L);

        authService.register(request);

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo(clientId);
    }

    @Test
    void register_withAttributes_serializesAsJson() {
        RegisterRequest request = RegisterRequest.builder()
                .externalId("alice")
                .password("secret123")
                .attributes(Map.of("department", "engineering", "level", 5))
                .build();

        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.existsByClientIdAndExternalId(clientId, "alice")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject s = invocation.getArgument(0);
            s.setId(subjectId);
            return s;
        });
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(900000L);

        authService.register(request);

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        String attrs = captor.getValue().getAttributes();
        assertThat(attrs).contains("department");
        assertThat(attrs).contains("engineering");
    }

    @Test
    void register_withNullAttributes_defaultsToEmptyJson() {
        RegisterRequest request = RegisterRequest.builder()
                .externalId("alice")
                .password("secret123")
                .attributes(null)
                .build();

        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.existsByClientIdAndExternalId(clientId, "alice")).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject s = invocation.getArgument(0);
            s.setId(subjectId);
            return s;
        });
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(900000L);

        authService.register(request);

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getAttributes()).isEqualTo("{}");
    }

    @Test
    void register_duplicateExternalId_throwsConflictException() {
        RegisterRequest request = RegisterRequest.builder()
                .externalId("alice")
                .password("secret123")
                .build();

        when(subjectRepository.existsByClientIdAndExternalId(clientId, "alice")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice")
                .hasMessageContaining("already exists");

        verify(subjectRepository, never()).save(any());
    }

    // =========================================================================
    // Login Tests
    // =========================================================================

    @Test
    void login_success_returnsAuthResponseWithJwt() {
        UUID subjectId = UUID.randomUUID();
        Subject subject = Subject.builder()
                .id(subjectId)
                .clientId(clientId)
                .externalId("alice")
                .passwordHash("$2a$hashed")
                .createdAt(Instant.now())
                .build();

        LoginRequest request = LoginRequest.builder()
                .externalId("alice")
                .password("secret123")
                .build();

        when(subjectRepository.findByClientIdAndExternalId(clientId, "alice"))
                .thenReturn(Optional.of(subject));
        when(passwordEncoder.matches("secret123", "$2a$hashed")).thenReturn(true);
        when(jwtTokenProvider.generateToken(subjectId, clientId, "alice")).thenReturn("jwt-token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(900000L);

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("jwt-token");
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900000L);
        assertThat(response.getSubjectId()).isEqualTo(subjectId);
        assertThat(response.getExternalId()).isEqualTo("alice");
    }

    @Test
    void login_unknownExternalId_throwsUnauthorized() {
        LoginRequest request = LoginRequest.builder()
                .externalId("nonexistent")
                .password("secret123")
                .build();

        when(subjectRepository.findByClientIdAndExternalId(clientId, "nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_wrongPassword_throwsUnauthorized() {
        Subject subject = Subject.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .externalId("alice")
                .passwordHash("$2a$hashed")
                .createdAt(Instant.now())
                .build();

        LoginRequest request = LoginRequest.builder()
                .externalId("alice")
                .password("wrong-password")
                .build();

        when(subjectRepository.findByClientIdAndExternalId(clientId, "alice"))
                .thenReturn(Optional.of(subject));
        when(passwordEncoder.matches("wrong-password", "$2a$hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid credentials");
    }

    @Test
    void login_usesCorrectTenantContext() {
        UUID subjectId = UUID.randomUUID();
        Subject subject = Subject.builder()
                .id(subjectId)
                .clientId(clientId)
                .externalId("alice")
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build();

        LoginRequest request = LoginRequest.builder()
                .externalId("alice")
                .password("pass")
                .build();

        when(subjectRepository.findByClientIdAndExternalId(eq(clientId), eq("alice")))
                .thenReturn(Optional.of(subject));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);
        when(jwtTokenProvider.generateToken(any(), any(), any())).thenReturn("token");
        when(jwtTokenProvider.getExpirationMs()).thenReturn(900000L);

        authService.login(request);

        // Verify the query was scoped to the current tenant
        verify(subjectRepository).findByClientIdAndExternalId(clientId, "alice");
    }
}
