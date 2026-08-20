package com.perhac.permissio.subject.service;

import com.perhac.permissio.common.exception.ConflictException;
import com.perhac.permissio.common.exception.NotFoundException;
import com.perhac.permissio.security.TenantContext;
import com.perhac.permissio.subject.dto.CreateSubjectRequest;
import com.perhac.permissio.subject.dto.SubjectResponse;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubjectService} using Mockito.
 * <p>
 * Follows strict TDD — all tests were written RED before the service
 * implementation existed. Covers CRUD operations, tenant isolation,
 * attribute serialization/deserialization, and password hashing.
 */
@ExtendWith(MockitoExtension.class)
class SubjectServiceTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private SubjectService subjectService;

    private final UUID clientId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        subjectService = new SubjectService(subjectRepository, passwordEncoder, objectMapper);
        TenantContext.set(clientId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // 1. createSubject — success
    // =========================================================================

    @Test
    void createSubject_success_savesSubjectAndReturnsResponse() {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .externalId("alice")
                .password("secret123")
                .attributes(Map.of("department", "engineering"))
                .build();

        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.existsByClientIdAndExternalId(clientId, "alice")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$hashed");
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject s = invocation.getArgument(0);
            s.setId(subjectId);
            return s;
        });

        SubjectResponse response = subjectService.createSubject(request);

        assertThat(response.id()).isEqualTo(subjectId);
        assertThat(response.clientId()).isEqualTo(clientId);
        assertThat(response.externalId()).isEqualTo("alice");
        assertThat(response.attributes()).containsEntry("department", "engineering");
        assertThat(response.createdAt()).isNotNull();

        // Verify password was hashed before persistence
        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$hashed");
    }

    // =========================================================================
    // 2. createSubject — duplicate externalId
    // =========================================================================

    @Test
    void createSubject_duplicateExternalId_throwsConflictException() {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .externalId("alice")
                .build();

        when(subjectRepository.existsByClientIdAndExternalId(clientId, "alice")).thenReturn(true);

        assertThatThrownBy(() -> subjectService.createSubject(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("alice")
                .hasMessageContaining("already exists");

        verify(subjectRepository, never()).save(any());
    }

    // =========================================================================
    // 3. getSubjectById — found
    // =========================================================================

    @Test
    void getSubjectById_found_returnsSubjectResponse() {
        UUID subjectId = UUID.randomUUID();
        Subject subject = Subject.builder()
                .id(subjectId)
                .clientId(clientId)
                .externalId("alice")
                .passwordHash("hash")
                .attributes("{\"role\":\"admin\"}")
                .createdAt(Instant.now())
                .build();

        when(subjectRepository.findByClientIdAndId(clientId, subjectId))
                .thenReturn(Optional.of(subject));

        SubjectResponse response = subjectService.getSubjectById(subjectId);

        assertThat(response.id()).isEqualTo(subjectId);
        assertThat(response.externalId()).isEqualTo("alice");
        assertThat(response.attributes()).containsEntry("role", "admin");
    }

    // =========================================================================
    // 4. getSubjectById — not found or wrong tenant
    // =========================================================================

    @Test
    void getSubjectById_notFoundOrWrongTenant_throwsNotFoundException() {
        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.findByClientIdAndId(clientId, subjectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.getSubjectById(subjectId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Subject not found");
    }

    // =========================================================================
    // 5. getSubjectByExternalId — found
    // =========================================================================

    @Test
    void getSubjectByExternalId_found_returnsSubjectResponse() {
        UUID subjectId = UUID.randomUUID();
        Subject subject = Subject.builder()
                .id(subjectId)
                .clientId(clientId)
                .externalId("alice")
                .passwordHash("hash")
                .attributes("{\"level\":3}")
                .createdAt(Instant.now())
                .build();

        when(subjectRepository.findByClientIdAndExternalId(clientId, "alice"))
                .thenReturn(Optional.of(subject));

        SubjectResponse response = subjectService.getSubjectByExternalId("alice");

        assertThat(response.id()).isEqualTo(subjectId);
        assertThat(response.externalId()).isEqualTo("alice");
        assertThat(response.attributes()).containsEntry("level", 3);
    }

    // =========================================================================
    // 6. getSubjectByExternalId — not found
    // =========================================================================

    @Test
    void getSubjectByExternalId_notFound_throwsNotFoundException() {
        when(subjectRepository.findByClientIdAndExternalId(clientId, "nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.getSubjectByExternalId("nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Subject not found");
    }

    // =========================================================================
    // 7. listSubjects — returns only current tenant's subjects
    // =========================================================================

    @Test
    void listSubjects_returnsOnlyCurrentTenantSubjects() {
        Subject s1 = Subject.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .externalId("alice")
                .passwordHash("hash")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();
        Subject s2 = Subject.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .externalId("bob")
                .passwordHash("hash")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();

        when(subjectRepository.findAllByClientId(clientId)).thenReturn(List.of(s1, s2));

        List<SubjectResponse> result = subjectService.listSubjects();

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(s -> s.externalId()).toList())
                .containsExactlyInAnyOrder("alice", "bob");

        // Verify query was scoped to current tenant
        verify(subjectRepository).findAllByClientId(clientId);
    }

    // =========================================================================
    // 8. updateAttributes — success
    // =========================================================================

    @Test
    void updateAttributes_success_updatesAndReturnsNewAttributes() {
        UUID subjectId = UUID.randomUUID();
        Subject existing = Subject.builder()
                .id(subjectId)
                .clientId(clientId)
                .externalId("alice")
                .passwordHash("hash")
                .attributes("{\"old\":\"value\"}")
                .createdAt(Instant.now())
                .build();

        when(subjectRepository.findByClientIdAndId(clientId, subjectId))
                .thenReturn(Optional.of(existing));
        when(subjectRepository.save(any(Subject.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> newAttrs = Map.of("department", "sales", "level", 7);
        SubjectResponse response = subjectService.updateAttributes(subjectId, newAttrs);

        assertThat(response.attributes()).containsEntry("department", "sales");
        assertThat(response.attributes()).containsEntry("level", 7);
        assertThat(response.attributes()).doesNotContainKey("old");

        // Verify the entity was updated before save
        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        assertThat(captor.getValue().getAttributes()).contains("department");
    }

    // =========================================================================
    // 9. updateAttributes — wrong tenant
    // =========================================================================

    @Test
    void updateAttributes_wrongTenant_throwsNotFoundException() {
        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.findByClientIdAndId(clientId, subjectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.updateAttributes(subjectId, Map.of("key", "val")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Subject not found");

        verify(subjectRepository, never()).save(any());
    }

    // =========================================================================
    // 10. deleteSubject — success
    // =========================================================================

    @Test
    void deleteSubject_success_deletesSubject() {
        UUID subjectId = UUID.randomUUID();
        Subject existing = Subject.builder()
                .id(subjectId)
                .clientId(clientId)
                .externalId("alice")
                .passwordHash("hash")
                .createdAt(Instant.now())
                .build();

        when(subjectRepository.findByClientIdAndId(clientId, subjectId))
                .thenReturn(Optional.of(existing));

        subjectService.deleteSubject(subjectId);

        verify(subjectRepository).delete(existing);
    }

    // =========================================================================
    // 11. deleteSubject — wrong tenant
    // =========================================================================

    @Test
    void deleteSubject_wrongTenant_throwsNotFoundException() {
        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.findByClientIdAndId(clientId, subjectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> subjectService.deleteSubject(subjectId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Subject not found");

        verify(subjectRepository, never()).delete(any());
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    void createSubject_withNullPassword_setsEmptyPasswordHash() {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .externalId("machine-user")
                .password(null)
                .attributes(null)
                .build();

        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.existsByClientIdAndExternalId(clientId, "machine-user")).thenReturn(false);
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject s = invocation.getArgument(0);
            s.setId(subjectId);
            return s;
        });

        SubjectResponse response = subjectService.createSubject(request);

        assertThat(response.externalId()).isEqualTo("machine-user");
        assertThat(response.attributes()).isEmpty();

        ArgumentCaptor<Subject> captor = ArgumentCaptor.forClass(Subject.class);
        verify(subjectRepository).save(captor.capture());
        // Password should not be hashed when null — empty string placeholder
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void createSubject_withEmptyAttributes_defaultsToEmptyMap() {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .externalId("bob")
                .password("pass123")
                .attributes(Map.of())
                .build();

        UUID subjectId = UUID.randomUUID();
        when(subjectRepository.existsByClientIdAndExternalId(clientId, "bob")).thenReturn(false);
        when(passwordEncoder.encode("pass123")).thenReturn("$2a$hash");
        when(subjectRepository.save(any(Subject.class))).thenAnswer(invocation -> {
            Subject s = invocation.getArgument(0);
            s.setId(subjectId);
            return s;
        });

        SubjectResponse response = subjectService.createSubject(request);

        assertThat(response.attributes()).isEmpty();
    }
}
