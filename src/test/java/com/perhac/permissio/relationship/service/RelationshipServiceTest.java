package com.perhac.permissio.relationship.service;

import com.perhac.permissio.common.exception.ConflictException;
import com.perhac.permissio.common.exception.NotFoundException;
import com.perhac.permissio.relationship.dto.CreateRelationshipRequest;
import com.perhac.permissio.relationship.dto.RelationshipResponse;
import com.perhac.permissio.relationship.entity.Relation;
import com.perhac.permissio.relationship.entity.Relationship;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.security.TenantContext;
import com.perhac.permissio.subject.entity.Subject;
import com.perhac.permissio.subject.repository.SubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RelationshipService} using Mockito.
 * <p>
 * Follows strict TDD discipline. Covers CRUD operations, relational integrity checks,
 * tenant isolation, duplicate prevention, and dynamic filtering.
 */
@ExtendWith(MockitoExtension.class)
class RelationshipServiceTest {

    @Mock
    private RelationshipRepository relationshipRepository;

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private ResourceRepository resourceRepository;

    private RelationshipService relationshipService;

    private final UUID clientId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        relationshipService = new RelationshipService(relationshipRepository, subjectRepository, resourceRepository);
        TenantContext.set(clientId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // 1. createRelationship — success
    // =========================================================================

    @Test
    void createRelationship_success_savesRelationshipAndReturnsResponse() {
        UUID subjectId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID relId = UUID.randomUUID();
        Instant now = Instant.now();

        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(Relation.OWNER)
                .build();

        when(subjectRepository.findByClientIdAndId(clientId, subjectId))
                .thenReturn(Optional.of(Subject.builder().id(subjectId).clientId(clientId).build()));
        when(resourceRepository.findByClientIdAndId(clientId, resourceId))
                .thenReturn(Optional.of(Resource.builder().id(resourceId).clientId(clientId).build()));
        when(relationshipRepository.existsByClientIdAndSubjectIdAndResourceIdAndRelation(
                clientId, subjectId, resourceId, Relation.OWNER))
                .thenReturn(false);

        when(relationshipRepository.save(any(Relationship.class)))
                .thenAnswer(inv -> {
                    Relationship r = inv.getArgument(0);
                    return Relationship.builder()
                            .id(relId)
                            .clientId(r.getClientId())
                            .subjectId(r.getSubjectId())
                            .resourceId(r.getResourceId())
                            .relation(r.getRelation())
                            .createdAt(now)
                            .build();
                });

        RelationshipResponse response = relationshipService.createRelationship(request);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(relId);
        assertThat(response.clientId()).isEqualTo(clientId);
        assertThat(response.subjectId()).isEqualTo(subjectId);
        assertThat(response.resourceId()).isEqualTo(resourceId);
        assertThat(response.relation()).isEqualTo(Relation.OWNER);

        ArgumentCaptor<Relationship> captor = ArgumentCaptor.forClass(Relationship.class);
        verify(relationshipRepository).save(captor.capture());
        Relationship saved = captor.getValue();
        assertThat(saved.getClientId()).isEqualTo(clientId);
        assertThat(saved.getSubjectId()).isEqualTo(subjectId);
        assertThat(saved.getResourceId()).isEqualTo(resourceId);
        assertThat(saved.getRelation()).isEqualTo(Relation.OWNER);
    }

    // =========================================================================
    // 2. createRelationship — error cases
    // =========================================================================

    @Test
    void createRelationship_missingSubject_throwsNotFoundException() {
        UUID subjectId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(Relation.MEMBER)
                .build();

        when(subjectRepository.findByClientIdAndId(clientId, subjectId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.createRelationship(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Subject not found");

        verify(resourceRepository, never()).findByClientIdAndId(any(), any());
        verify(relationshipRepository, never()).save(any());
    }

    @Test
    void createRelationship_missingResource_throwsNotFoundException() {
        UUID subjectId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(Relation.MEMBER)
                .build();

        when(subjectRepository.findByClientIdAndId(clientId, subjectId))
                .thenReturn(Optional.of(Subject.builder().id(subjectId).clientId(clientId).build()));
        when(resourceRepository.findByClientIdAndId(clientId, resourceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.createRelationship(request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Resource not found");

        verify(relationshipRepository, never()).save(any());
    }

    @Test
    void createRelationship_duplicateTuple_throwsConflictException() {
        UUID subjectId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(Relation.OWNER)
                .build();

        when(subjectRepository.findByClientIdAndId(clientId, subjectId))
                .thenReturn(Optional.of(Subject.builder().id(subjectId).clientId(clientId).build()));
        when(resourceRepository.findByClientIdAndId(clientId, resourceId))
                .thenReturn(Optional.of(Resource.builder().id(resourceId).clientId(clientId).build()));
        when(relationshipRepository.existsByClientIdAndSubjectIdAndResourceIdAndRelation(
                clientId, subjectId, resourceId, Relation.OWNER))
                .thenReturn(true);

        assertThatThrownBy(() -> relationshipService.createRelationship(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Relationship tuple already exists");

        verify(relationshipRepository, never()).save(any());
    }

    // =========================================================================
    // 3. getRelationshipById — found and not found
    // =========================================================================

    @Test
    void getRelationshipById_found_returnsRelationshipResponse() {
        UUID relId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        Instant now = Instant.now();

        Relationship relationship = Relationship.builder()
                .id(relId)
                .clientId(clientId)
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(Relation.MANAGER)
                .createdAt(now)
                .build();

        when(relationshipRepository.findByClientIdAndId(clientId, relId))
                .thenReturn(Optional.of(relationship));

        RelationshipResponse response = relationshipService.getRelationshipById(relId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(relId);
        assertThat(response.relation()).isEqualTo(Relation.MANAGER);
    }

    @Test
    void getRelationshipById_notFoundOrWrongTenant_throwsNotFoundException() {
        UUID relId = UUID.randomUUID();

        when(relationshipRepository.findByClientIdAndId(clientId, relId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.getRelationshipById(relId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Relationship not found");
    }

    // =========================================================================
    // 4. listRelationships — filtering
    // =========================================================================

    @Test
    void listRelationships_filteredBySubjectIdAndResourceId_returnsMatching() {
        UUID subjectId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();

        Relationship rel = Relationship.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(Relation.LEAD)
                .createdAt(Instant.now())
                .build();

        when(relationshipRepository.findByClientIdAndSubjectIdAndResourceId(clientId, subjectId, resourceId))
                .thenReturn(List.of(rel));

        List<RelationshipResponse> responses = relationshipService.listRelationships(subjectId, resourceId);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).relation()).isEqualTo(Relation.LEAD);
        verify(relationshipRepository).findByClientIdAndSubjectIdAndResourceId(clientId, subjectId, resourceId);
    }

    @Test
    void listRelationships_filteredBySubjectIdOnly_returnsMatching() {
        UUID subjectId = UUID.randomUUID();

        Relationship rel = Relationship.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .subjectId(subjectId)
                .resourceId(UUID.randomUUID())
                .relation(Relation.MEMBER)
                .createdAt(Instant.now())
                .build();

        when(relationshipRepository.findByClientIdAndSubjectId(clientId, subjectId))
                .thenReturn(List.of(rel));

        List<RelationshipResponse> responses = relationshipService.listRelationships(subjectId, null);

        assertThat(responses).hasSize(1);
        verify(relationshipRepository).findByClientIdAndSubjectId(clientId, subjectId);
    }

    @Test
    void listRelationships_filteredByResourceIdOnly_returnsMatching() {
        UUID resourceId = UUID.randomUUID();

        Relationship rel = Relationship.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .subjectId(UUID.randomUUID())
                .resourceId(resourceId)
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build();

        when(relationshipRepository.findByClientIdAndResourceId(clientId, resourceId))
                .thenReturn(List.of(rel));

        List<RelationshipResponse> responses = relationshipService.listRelationships(null, resourceId);

        assertThat(responses).hasSize(1);
        verify(relationshipRepository).findByClientIdAndResourceId(clientId, resourceId);
    }

    @Test
    void listRelationships_all_returnsTenantRelationships() {
        Relationship rel1 = Relationship.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .subjectId(UUID.randomUUID())
                .resourceId(UUID.randomUUID())
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build();

        Relationship rel2 = Relationship.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .subjectId(UUID.randomUUID())
                .resourceId(UUID.randomUUID())
                .relation(Relation.MEMBER)
                .createdAt(Instant.now())
                .build();

        when(relationshipRepository.findAllByClientId(clientId))
                .thenReturn(List.of(rel1, rel2));

        List<RelationshipResponse> responses = relationshipService.listRelationships(null, null);

        assertThat(responses).hasSize(2);
        verify(relationshipRepository).findAllByClientId(clientId);
    }

    // =========================================================================
    // 5. deleteRelationship — success and not found
    // =========================================================================

    @Test
    void deleteRelationship_success_deletesRelationship() {
        UUID relId = UUID.randomUUID();
        Relationship relationship = Relationship.builder()
                .id(relId)
                .clientId(clientId)
                .subjectId(UUID.randomUUID())
                .resourceId(UUID.randomUUID())
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build();

        when(relationshipRepository.findByClientIdAndId(clientId, relId))
                .thenReturn(Optional.of(relationship));

        relationshipService.deleteRelationship(relId);

        verify(relationshipRepository).delete(relationship);
    }

    @Test
    void deleteRelationship_notFoundOrWrongTenant_throwsNotFoundException() {
        UUID relId = UUID.randomUUID();

        when(relationshipRepository.findByClientIdAndId(clientId, relId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> relationshipService.deleteRelationship(relId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Relationship not found");

        verify(relationshipRepository, never()).delete(any());
    }
}
