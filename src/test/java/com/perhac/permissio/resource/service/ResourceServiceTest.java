package com.perhac.permissio.resource.service;

import com.perhac.permissio.common.exception.ConflictException;
import com.perhac.permissio.common.exception.NotFoundException;
import com.perhac.permissio.resource.dto.CreateResourceRequest;
import com.perhac.permissio.resource.dto.ResourceResponse;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.security.TenantContext;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
 * Unit tests for {@link ResourceService} using Mockito.
 * <p>
 * Follows strict TDD discipline. Covers CRUD operations, compound uniqueness,
 * tenant isolation, JSON attribute serialization/deserialization, and type filtering.
 */
@ExtendWith(MockitoExtension.class)
class ResourceServiceTest {

    @Mock
    private ResourceRepository resourceRepository;

    private ResourceService resourceService;

    private final UUID clientId = UUID.randomUUID();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        resourceService = new ResourceService(resourceRepository, objectMapper);
        TenantContext.set(clientId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // =========================================================================
    // 1. createResource — success
    // =========================================================================

    @Test
    void createResource_success_savesResourceAndReturnsResponse() {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes(Map.of("department", "engineering", "confidential", true))
                .build();

        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.existsByClientIdAndResourceTypeAndExternalId(clientId, "DOCUMENT", "doc-101"))
                .thenReturn(false);
        when(resourceRepository.save(any(Resource.class))).thenAnswer(invocation -> {
            Resource r = invocation.getArgument(0);
            r.setId(resourceId);
            return r;
        });

        ResourceResponse response = resourceService.createResource(request);

        assertThat(response.id()).isEqualTo(resourceId);
        assertThat(response.clientId()).isEqualTo(clientId);
        assertThat(response.resourceType()).isEqualTo("DOCUMENT");
        assertThat(response.externalId()).isEqualTo("doc-101");
        assertThat(response.attributes()).containsEntry("department", "engineering");
        assertThat(response.attributes()).containsEntry("confidential", true);
        assertThat(response.createdAt()).isNotNull();

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceRepository).save(captor.capture());
        assertThat(captor.getValue().getClientId()).isEqualTo(clientId);
        assertThat(captor.getValue().getResourceType()).isEqualTo("DOCUMENT");
        assertThat(captor.getValue().getExternalId()).isEqualTo("doc-101");
    }

    // =========================================================================
    // 2. createResource — duplicate (resourceType, externalId)
    // =========================================================================

    @Test
    void createResource_duplicateTypeAndExternalId_throwsConflictException() {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .build();

        when(resourceRepository.existsByClientIdAndResourceTypeAndExternalId(clientId, "DOCUMENT", "doc-101"))
                .thenReturn(true);

        assertThatThrownBy(() -> resourceService.createResource(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("DOCUMENT")
                .hasMessageContaining("doc-101")
                .hasMessageContaining("already exists");

        verify(resourceRepository, never()).save(any());
    }

    // =========================================================================
    // 3. createResource — with null / empty attributes
    // =========================================================================

    @Test
    void createResource_withNullAttributes_defaultsToEmptyMap() {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .resourceType("FOLDER")
                .externalId("f-101")
                .attributes(null)
                .build();

        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.existsByClientIdAndResourceTypeAndExternalId(clientId, "FOLDER", "f-101"))
                .thenReturn(false);
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(resourceId);
            return r;
        });

        ResourceResponse response = resourceService.createResource(request);

        assertThat(response.attributes()).isEmpty();

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceRepository).save(captor.capture());
        assertThat(captor.getValue().getAttributes()).isEqualTo("{}");
    }

    @Test
    void createResource_withEmptyAttributes_defaultsToEmptyMap() {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .resourceType("FOLDER")
                .externalId("f-102")
                .attributes(Map.of())
                .build();

        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.existsByClientIdAndResourceTypeAndExternalId(clientId, "FOLDER", "f-102"))
                .thenReturn(false);
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> {
            Resource r = inv.getArgument(0);
            r.setId(resourceId);
            return r;
        });

        ResourceResponse response = resourceService.createResource(request);

        assertThat(response.attributes()).isEmpty();
    }

    // =========================================================================
    // 4. getResourceById — found
    // =========================================================================

    @Test
    void getResourceById_found_returnsResourceResponse() {
        UUID resourceId = UUID.randomUUID();
        Resource resource = Resource.builder()
                .id(resourceId)
                .clientId(clientId)
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{\"tier\":\"premium\"}")
                .createdAt(Instant.now())
                .build();

        when(resourceRepository.findByClientIdAndId(clientId, resourceId))
                .thenReturn(Optional.of(resource));

        ResourceResponse response = resourceService.getResourceById(resourceId);

        assertThat(response.id()).isEqualTo(resourceId);
        assertThat(response.resourceType()).isEqualTo("DOCUMENT");
        assertThat(response.externalId()).isEqualTo("doc-101");
        assertThat(response.attributes()).containsEntry("tier", "premium");
    }

    // =========================================================================
    // 5. getResourceById — not found or wrong tenant
    // =========================================================================

    @Test
    void getResourceById_notFoundOrWrongTenant_throwsNotFoundException() {
        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.findByClientIdAndId(clientId, resourceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.getResourceById(resourceId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Resource not found");
    }

    // =========================================================================
    // 6. getResourceByTypeAndExternalId — found
    // =========================================================================

    @Test
    void getResourceByTypeAndExternalId_found_returnsResourceResponse() {
        UUID resourceId = UUID.randomUUID();
        Resource resource = Resource.builder()
                .id(resourceId)
                .clientId(clientId)
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{\"version\":2}")
                .createdAt(Instant.now())
                .build();

        when(resourceRepository.findByClientIdAndResourceTypeAndExternalId(clientId, "DOCUMENT", "doc-101"))
                .thenReturn(Optional.of(resource));

        ResourceResponse response = resourceService.getResourceByTypeAndExternalId("DOCUMENT", "doc-101");

        assertThat(response.id()).isEqualTo(resourceId);
        assertThat(response.resourceType()).isEqualTo("DOCUMENT");
        assertThat(response.externalId()).isEqualTo("doc-101");
        assertThat(response.attributes()).containsEntry("version", 2);
    }

    // =========================================================================
    // 7. getResourceByTypeAndExternalId — not found
    // =========================================================================

    @Test
    void getResourceByTypeAndExternalId_notFound_throwsNotFoundException() {
        when(resourceRepository.findByClientIdAndResourceTypeAndExternalId(clientId, "DOCUMENT", "nonexistent"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.getResourceByTypeAndExternalId("DOCUMENT", "nonexistent"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Resource not found");
    }

    // =========================================================================
    // 8. listResources — all resources for current tenant
    // =========================================================================

    @Test
    void listResources_all_returnsTenantResources() {
        Resource r1 = Resource.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("DOCUMENT")
                .externalId("doc-1")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();

        Resource r2 = Resource.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("FOLDER")
                .externalId("folder-1")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();

        when(resourceRepository.findAllByClientId(clientId)).thenReturn(List.of(r1, r2));

        List<ResourceResponse> result = resourceService.listResources(null);

        assertThat(result).hasSize(2);
        assertThat(result.stream().map(r -> r.externalId()).toList())
                .containsExactlyInAnyOrder("doc-1", "folder-1");

        verify(resourceRepository).findAllByClientId(clientId);
        verify(resourceRepository, never()).findAllByClientIdAndResourceType(any(), any());
    }

    // =========================================================================
    // 9. listResources — filtered by type
    // =========================================================================

    @Test
    void listResources_filteredByType_returnsMatchingResourcesOnly() {
        Resource r1 = Resource.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("DOCUMENT")
                .externalId("doc-1")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();

        when(resourceRepository.findAllByClientIdAndResourceType(clientId, "DOCUMENT"))
                .thenReturn(List.of(r1));

        List<ResourceResponse> result = resourceService.listResources("DOCUMENT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).externalId()).isEqualTo("doc-1");

        verify(resourceRepository).findAllByClientIdAndResourceType(clientId, "DOCUMENT");
        verify(resourceRepository, never()).findAllByClientId(any());
    }

    // =========================================================================
    // 10. updateAttributes — success
    // =========================================================================

    @Test
    void updateAttributes_success_updatesAndReturnsNewAttributes() {
        UUID resourceId = UUID.randomUUID();
        Resource existing = Resource.builder()
                .id(resourceId)
                .clientId(clientId)
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{\"old\":\"data\"}")
                .createdAt(Instant.now())
                .build();

        when(resourceRepository.findByClientIdAndId(clientId, resourceId))
                .thenReturn(Optional.of(existing));
        when(resourceRepository.save(any(Resource.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> newAttrs = Map.of("classification", "top-secret", "level", 5);
        ResourceResponse response = resourceService.updateAttributes(resourceId, newAttrs);

        assertThat(response.attributes()).containsEntry("classification", "top-secret");
        assertThat(response.attributes()).containsEntry("level", 5);
        assertThat(response.attributes()).doesNotContainKey("old");

        ArgumentCaptor<Resource> captor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceRepository).save(captor.capture());
        assertThat(captor.getValue().getAttributes()).contains("classification");
    }

    // =========================================================================
    // 11. updateAttributes — wrong tenant or not found
    // =========================================================================

    @Test
    void updateAttributes_wrongTenantOrNotFound_throwsNotFoundException() {
        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.findByClientIdAndId(clientId, resourceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.updateAttributes(resourceId, Map.of("k", "v")))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Resource not found");

        verify(resourceRepository, never()).save(any());
    }

    // =========================================================================
    // 12. deleteResource — success
    // =========================================================================

    @Test
    void deleteResource_success_deletesResource() {
        UUID resourceId = UUID.randomUUID();
        Resource existing = Resource.builder()
                .id(resourceId)
                .clientId(clientId)
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();

        when(resourceRepository.findByClientIdAndId(clientId, resourceId))
                .thenReturn(Optional.of(existing));

        resourceService.deleteResource(resourceId);

        verify(resourceRepository).delete(existing);
    }

    // =========================================================================
    // 13. deleteResource — wrong tenant or not found
    // =========================================================================

    @Test
    void deleteResource_wrongTenantOrNotFound_throwsNotFoundException() {
        UUID resourceId = UUID.randomUUID();
        when(resourceRepository.findByClientIdAndId(clientId, resourceId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resourceService.deleteResource(resourceId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Resource not found");

        verify(resourceRepository, never()).delete(any());
    }
}
