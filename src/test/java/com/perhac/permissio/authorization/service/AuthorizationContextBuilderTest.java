package com.perhac.permissio.authorization.service;

import com.perhac.permissio.authorization.dto.AuthorizeRequest;
import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.common.exception.NotFoundException;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.relationship.entity.Relation;
import com.perhac.permissio.relationship.entity.Relationship;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.subject.entity.Subject;
import com.perhac.permissio.subject.repository.SubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationContextBuilderTest {

    @Mock
    private SubjectRepository subjectRepository;

    @Mock
    private ResourceRepository resourceRepository;

    @Mock
    private RelationshipRepository relationshipRepository;

    @InjectMocks
    private AuthorizationContextBuilder contextBuilder;

    private UUID clientId;
    private UUID subjectId;
    private UUID resourceId;
    private Subject subject;
    private Resource resource;
    private AuthorizeRequest request;

    @BeforeEach
    void setUp() {
        clientId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        resourceId = UUID.randomUUID();

        subject = Subject.builder()
                .id(subjectId)
                .clientId(clientId)
                .externalId("alice")
                .passwordHash("pwd")
                .createdAt(Instant.now())
                .build();

        resource = Resource.builder()
                .id(resourceId)
                .clientId(clientId)
                .resourceType("document")
                .externalId("doc-1")
                .createdAt(Instant.now())
                .build();

        request = AuthorizeRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .action(Action.READ)
                .build();
    }

    @Test
    @DisplayName("Builds complete AuthorizationContext when subject and resource exist under tenant")
    void build_validEntities_returnsPopulatedContext() {
        Relationship rel = Relationship.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(Relation.MEMBER)
                .createdAt(Instant.now())
                .build();

        when(subjectRepository.findByClientIdAndId(clientId, subjectId)).thenReturn(Optional.of(subject));
        when(resourceRepository.findByClientIdAndId(clientId, resourceId)).thenReturn(Optional.of(resource));
        when(relationshipRepository.findByClientIdAndSubjectId(clientId, subjectId)).thenReturn(List.of(rel));

        AuthorizationContext context = contextBuilder.build(clientId, request);

        assertThat(context).isNotNull();
        assertThat(context.clientId()).isEqualTo(clientId);
        assertThat(context.subject()).isEqualTo(subject);
        assertThat(context.resource()).isEqualTo(resource);
        assertThat(context.action()).isEqualTo(Action.READ);
        assertThat(context.relationships()).containsExactly(rel);

        verify(subjectRepository).findByClientIdAndId(clientId, subjectId);
        verify(resourceRepository).findByClientIdAndId(clientId, resourceId);
        verify(relationshipRepository).findByClientIdAndSubjectId(clientId, subjectId);
    }

    @Test
    @DisplayName("Throws NotFoundException when subject does not belong to tenant")
    void build_subjectNotFound_throwsNotFoundException() {
        when(subjectRepository.findByClientIdAndId(clientId, subjectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contextBuilder.build(clientId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Subject not found");
    }

    @Test
    @DisplayName("Throws NotFoundException when resource does not belong to tenant")
    void build_resourceNotFound_throwsNotFoundException() {
        when(subjectRepository.findByClientIdAndId(clientId, subjectId)).thenReturn(Optional.of(subject));
        when(resourceRepository.findByClientIdAndId(clientId, resourceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> contextBuilder.build(clientId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Resource not found");
    }
}
