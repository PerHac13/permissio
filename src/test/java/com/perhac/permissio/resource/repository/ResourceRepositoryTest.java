package com.perhac.permissio.resource.repository;

import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.resource.entity.Resource;
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
 * Data JPA tests for {@link ResourceRepository} using H2 in-memory database.
 * <p>
 * Verifies CRUD operations, tenant-scoped queries, compound unique constraint
 * enforcement, and type filtering.
 */
@DataJpaTest
@ActiveProfiles("test")
class ResourceRepositoryTest {

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private ClientRepository clientRepository;

    private Client clientA;
    private Client clientB;

    @BeforeEach
    void setUp() {
        resourceRepository.deleteAll();
        clientRepository.deleteAll();

        clientA = clientRepository.save(Client.builder()
                .name("Acme HR")
                .apiKeyHash("hash-a")
                .createdAt(Instant.now())
                .build());

        clientB = clientRepository.save(Client.builder()
                .name("Beta Corp")
                .apiKeyHash("hash-b")
                .createdAt(Instant.now())
                .build());
    }

    @Test
    void saveAndRetrieveResource() {
        Resource resource = resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{\"classification\":\"confidential\"}")
                .createdAt(Instant.now())
                .build());

        assertThat(resource.getId()).isNotNull();

        Optional<Resource> found = resourceRepository.findById(resource.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getClientId()).isEqualTo(clientA.getId());
        assertThat(found.get().getResourceType()).isEqualTo("DOCUMENT");
        assertThat(found.get().getExternalId()).isEqualTo("doc-101");
        assertThat(found.get().getAttributes()).isEqualTo("{\"classification\":\"confidential\"}");
    }

    @Test
    void findByClientIdAndId_found() {
        Resource saved = resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        Optional<Resource> result = resourceRepository.findByClientIdAndId(
                clientA.getId(), saved.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getExternalId()).isEqualTo("doc-101");
    }

    @Test
    void findByClientIdAndId_notFound_wrongTenant() {
        Resource saved = resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        // Same resource ID but queried by different tenant — must not find
        Optional<Resource> result = resourceRepository.findByClientIdAndId(
                clientB.getId(), saved.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByClientIdAndResourceTypeAndExternalId_found() {
        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        Optional<Resource> result = resourceRepository.findByClientIdAndResourceTypeAndExternalId(
                clientA.getId(), "DOCUMENT", "doc-101");

        assertThat(result).isPresent();
        assertThat(result.get().getResourceType()).isEqualTo("DOCUMENT");
        assertThat(result.get().getExternalId()).isEqualTo("doc-101");
    }

    @Test
    void findByClientIdAndResourceTypeAndExternalId_notFound_wrongTenant() {
        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        Optional<Resource> result = resourceRepository.findByClientIdAndResourceTypeAndExternalId(
                clientB.getId(), "DOCUMENT", "doc-101");

        assertThat(result).isEmpty();
    }

    @Test
    void findByClientIdAndResourceTypeAndExternalId_notFound_wrongType() {
        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        Optional<Resource> result = resourceRepository.findByClientIdAndResourceTypeAndExternalId(
                clientA.getId(), "FOLDER", "doc-101");

        assertThat(result).isEmpty();
    }

    @Test
    void existsByClientIdAndResourceTypeAndExternalId_true() {
        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        assertThat(resourceRepository.existsByClientIdAndResourceTypeAndExternalId(
                clientA.getId(), "DOCUMENT", "doc-101")).isTrue();
    }

    @Test
    void existsByClientIdAndResourceTypeAndExternalId_false_nonexistent() {
        assertThat(resourceRepository.existsByClientIdAndResourceTypeAndExternalId(
                clientA.getId(), "DOCUMENT", "nonexistent")).isFalse();
    }

    @Test
    void existsByClientIdAndResourceTypeAndExternalId_false_wrongTenant() {
        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        assertThat(resourceRepository.existsByClientIdAndResourceTypeAndExternalId(
                clientB.getId(), "DOCUMENT", "doc-101")).isFalse();
    }

    @Test
    void existsByClientIdAndResourceTypeAndExternalId_false_wrongType() {
        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        assertThat(resourceRepository.existsByClientIdAndResourceTypeAndExternalId(
                clientA.getId(), "FOLDER", "doc-101")).isFalse();
    }

    @Test
    void uniqueConstraint_sameClientTypeAndExternalId_throwsException() {
        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        Resource duplicate = Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{\"updated\":true}")
                .createdAt(Instant.now())
                .build();

        assertThatThrownBy(() -> resourceRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameTypeAndExternalId_differentTenants_succeeds() {
        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        Resource crossTenant = resourceRepository.save(Resource.builder()
                .clientId(clientB.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        assertThat(crossTenant.getId()).isNotNull();
        assertThat(resourceRepository.findByClientIdAndResourceTypeAndExternalId(
                clientA.getId(), "DOCUMENT", "doc-101")).isPresent();
        assertThat(resourceRepository.findByClientIdAndResourceTypeAndExternalId(
                clientB.getId(), "DOCUMENT", "doc-101")).isPresent();
    }

    @Test
    void sameExternalId_differentResourceTypes_sameTenant_succeeds() {
        // Compound uniqueness allows DOCUMENT:101 and FOLDER:101 for the same tenant
        Resource doc = resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        Resource folder = resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("FOLDER")
                .externalId("101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        assertThat(doc.getId()).isNotNull();
        assertThat(folder.getId()).isNotNull();
        assertThat(resourceRepository.findByClientIdAndResourceTypeAndExternalId(
                clientA.getId(), "DOCUMENT", "101")).isPresent();
        assertThat(resourceRepository.findByClientIdAndResourceTypeAndExternalId(
                clientA.getId(), "FOLDER", "101")).isPresent();
    }

    @Test
    void findAllByClientId_returnsTenantResourcesOnly() {
        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-1")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("FOLDER")
                .externalId("folder-1")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        resourceRepository.save(Resource.builder()
                .clientId(clientB.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-2")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        List<Resource> clientAResources = resourceRepository.findAllByClientId(clientA.getId());
        assertThat(clientAResources).hasSize(2);
        assertThat(clientAResources).extracting(Resource::getExternalId)
                .containsExactlyInAnyOrder("doc-1", "folder-1");
    }

    @Test
    void findAllByClientIdAndResourceType_returnsFilteredResources() {
        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-1")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("doc-2")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("FOLDER")
                .externalId("folder-1")
                .attributes("{}")
                .createdAt(Instant.now())
                .build());

        List<Resource> docsOnly = resourceRepository.findAllByClientIdAndResourceType(
                clientA.getId(), "DOCUMENT");
        assertThat(docsOnly).hasSize(2);
        assertThat(docsOnly).extracting(Resource::getExternalId)
                .containsExactlyInAnyOrder("doc-1", "doc-2");
    }

    @Test
    void defaultAttributes_isEmptyJson() {
        Resource resource = resourceRepository.save(Resource.builder()
                .clientId(clientA.getId())
                .resourceType("DOCUMENT")
                .externalId("default-attrs-res")
                .createdAt(Instant.now())
                .build());

        Resource found = resourceRepository.findById(resource.getId()).orElseThrow();
        assertThat(found.getAttributes()).isEqualTo("{}");
    }
}
