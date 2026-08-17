package com.perhac.permissio.relationship.repository;

import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.relationship.entity.Relation;
import com.perhac.permissio.relationship.entity.Relationship;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.subject.entity.Subject;
import com.perhac.permissio.subject.repository.SubjectRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Data JPA integration tests for {@link RelationshipRepository} using H2 database.
 * <p>
 * Tests persistence, tenant-scoped queries, relational queries (by subject/resource),
 * and compound uniqueness enforcement.
 */
@DataJpaTest
@ActiveProfiles("test")
class RelationshipRepositoryTest {

    @Autowired
    private RelationshipRepository relationshipRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ClientRepository clientRepository;

    private Client clientA;
    private Client clientB;
    private Subject subjectA1;
    private Subject subjectA2;
    private Subject subjectB1;
    private Resource resourceA1;
    private Resource resourceA2;
    private Resource resourceB1;

    @BeforeEach
    void setUp() {
        relationshipRepository.deleteAll();
        resourceRepository.deleteAll();
        subjectRepository.deleteAll();
        clientRepository.deleteAll();

        clientA = clientRepository.save(Client.builder()
                .name("Tenant A")
                .apiKeyHash("hash-a")
                .createdAt(Instant.now())
                .build());

        clientB = clientRepository.save(Client.builder()
                .name("Tenant B")
                .apiKeyHash("hash-b")
                .createdAt(Instant.now())
                .build());

        subjectA1 = subjectRepository.save(Subject.builder()
                .clientId(clientA.getId())
                .externalId("user-a1")
                .passwordHash("pass")
                .createdAt(Instant.now())
                .build());

        subjectA2 = subjectRepository.save(Subject.builder()
                .clientId(clientA.getId())
                .externalId("user-a2")
                .passwordHash("pass")
                .createdAt(Instant.now())
                .build());

        subjectB1 = subjectRepository.save(Subject.builder()
                .clientId(clientB.getId())
                .externalId("user-b1")
                .passwordHash("pass")
                .createdAt(Instant.now())
                .build());

        resourceA1 = resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("document")
                .externalId("doc-a1")
                .createdAt(Instant.now())
                .build());

        resourceA2 = resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("document")
                .externalId("doc-a2")
                .createdAt(Instant.now())
                .build());

        resourceB1 = resourceRepository.save(Resource.builder()
                .clientId(clientB.getId())
                .resourceType("document")
                .externalId("doc-b1")
                .createdAt(Instant.now())
                .build());
    }

    @AfterEach
    void tearDown() {
        relationshipRepository.deleteAll();
        resourceRepository.deleteAll();
        subjectRepository.deleteAll();
        clientRepository.deleteAll();
    }

    @Test
    void saveAndRetrieveRelationship() {
        Relationship relationship = relationshipRepository.save(Relationship.builder()
                .clientId(clientA.getId())
                .subjectId(subjectA1.getId())
                .resourceId(resourceA1.getId())
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build());

        assertThat(relationship.getId()).isNotNull();

        Optional<Relationship> found = relationshipRepository.findByClientIdAndId(clientA.getId(), relationship.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getRelation()).isEqualTo(Relation.OWNER);
        assertThat(found.get().getSubjectId()).isEqualTo(subjectA1.getId());
        assertThat(found.get().getResourceId()).isEqualTo(resourceA1.getId());
    }

    @Test
    void findByClientIdAndId_notFound_wrongTenant() {
        Relationship relationship = relationshipRepository.save(Relationship.builder()
                .clientId(clientA.getId())
                .subjectId(subjectA1.getId())
                .resourceId(resourceA1.getId())
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build());

        Optional<Relationship> found = relationshipRepository.findByClientIdAndId(clientB.getId(), relationship.getId());
        assertThat(found).isEmpty();
    }

    @Test
    void findAllByClientId_returnsTenantRelationshipsOnly() {
        relationshipRepository.save(Relationship.builder()
                .clientId(clientA.getId())
                .subjectId(subjectA1.getId())
                .resourceId(resourceA1.getId())
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build());

        relationshipRepository.save(Relationship.builder()
                .clientId(clientB.getId())
                .subjectId(subjectB1.getId())
                .resourceId(resourceB1.getId())
                .relation(Relation.MEMBER)
                .createdAt(Instant.now())
                .build());

        List<Relationship> clientAResults = relationshipRepository.findAllByClientId(clientA.getId());
        assertThat(clientAResults).hasSize(1);
        assertThat(clientAResults.get(0).getClientId()).isEqualTo(clientA.getId());

        List<Relationship> clientBResults = relationshipRepository.findAllByClientId(clientB.getId());
        assertThat(clientBResults).hasSize(1);
        assertThat(clientBResults.get(0).getClientId()).isEqualTo(clientB.getId());
    }

    @Test
    void uniqueConstraint_duplicateTuple_throwsDataIntegrityViolationException() {
        relationshipRepository.saveAndFlush(Relationship.builder()
                .clientId(clientA.getId())
                .subjectId(subjectA1.getId())
                .resourceId(resourceA1.getId())
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build());

        assertThatThrownBy(() -> relationshipRepository.saveAndFlush(Relationship.builder()
                .clientId(clientA.getId())
                .subjectId(subjectA1.getId())
                .resourceId(resourceA1.getId())
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameSubjectAndResource_differentRelation_succeeds() {
        relationshipRepository.saveAndFlush(Relationship.builder()
                .clientId(clientA.getId())
                .subjectId(subjectA1.getId())
                .resourceId(resourceA1.getId())
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build());

        Relationship managerRel = relationshipRepository.saveAndFlush(Relationship.builder()
                .clientId(clientA.getId())
                .subjectId(subjectA1.getId())
                .resourceId(resourceA1.getId())
                .relation(Relation.MANAGER)
                .createdAt(Instant.now())
                .build());

        assertThat(managerRel.getId()).isNotNull();
    }

    @Test
    void findByClientIdAndSubjectId_returnsMatchingRelationships() {
        initTestData();

        List<Relationship> results = relationshipRepository.findByClientIdAndSubjectId(clientA.getId(), subjectA1.getId());
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Relationship::getResourceId)
                .containsExactlyInAnyOrder(resourceA1.getId(), resourceA2.getId());
    }

    @Test
    void findByClientIdAndResourceId_returnsMatchingRelationships() {
        initTestData();

        List<Relationship> results = relationshipRepository.findByClientIdAndResourceId(clientA.getId(), resourceA1.getId());
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Relationship::getSubjectId)
                .containsExactlyInAnyOrder(subjectA1.getId(), subjectA2.getId());
    }

    @Test
    void findByClientIdAndSubjectIdAndResourceId_returnsMatchingRelationships() {
        initTestData();

        List<Relationship> results = relationshipRepository.findByClientIdAndSubjectIdAndResourceId(
                clientA.getId(), subjectA1.getId(), resourceA1.getId());
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRelation()).isEqualTo(Relation.OWNER);
    }

    @Test
    void existsByClientIdAndSubjectIdAndResourceIdAndRelation_evaluatesCorrectly() {
        initTestData();

        boolean exists = relationshipRepository.existsByClientIdAndSubjectIdAndResourceIdAndRelation(
                clientA.getId(), subjectA1.getId(), resourceA1.getId(), Relation.OWNER);
        assertThat(exists).isTrue();

        boolean nonExistent = relationshipRepository.existsByClientIdAndSubjectIdAndResourceIdAndRelation(
                clientA.getId(), subjectA1.getId(), resourceA1.getId(), Relation.MANAGER);
        assertThat(nonExistent).isFalse();

        boolean otherTenant = relationshipRepository.existsByClientIdAndSubjectIdAndResourceIdAndRelation(
                clientB.getId(), subjectA1.getId(), resourceA1.getId(), Relation.OWNER);
        assertThat(otherTenant).isFalse();
    }

    private void initTestData() {
        relationshipRepository.save(Relationship.builder()
                .clientId(clientA.getId())
                .subjectId(subjectA1.getId())
                .resourceId(resourceA1.getId())
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build());

        relationshipRepository.save(Relationship.builder()
                .clientId(clientA.getId())
                .subjectId(subjectA1.getId())
                .resourceId(resourceA2.getId())
                .relation(Relation.MEMBER)
                .createdAt(Instant.now())
                .build());

        relationshipRepository.save(Relationship.builder()
                .clientId(clientA.getId())
                .subjectId(subjectA2.getId())
                .resourceId(resourceA1.getId())
                .relation(Relation.LEAD)
                .createdAt(Instant.now())
                .build());
    }
}
