package com.perhac.permissio.resource.service;

import com.perhac.permissio.common.exception.ConflictException;
import com.perhac.permissio.common.exception.NotFoundException;
import com.perhac.permissio.resource.dto.CreateResourceRequest;
import com.perhac.permissio.resource.dto.ResourceResponse;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.security.TenantContext;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic for tenant-scoped Resource CRUD and attribute management.
 * <p>
 * All operations enforce tenant isolation by reading the client ID from {@link TenantContext}
 * and scoping all database queries accordingly. Resources belonging to other tenants
 * return 404 (Not Found) to avoid leaking existence across tenant boundaries.
 */
@Service
public class ResourceService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE_REF = new TypeReference<>() {};

    private final ResourceRepository resourceRepository;
    private final ObjectMapper objectMapper;

    public ResourceService(ResourceRepository resourceRepository,
                           ObjectMapper objectMapper) {
        this.resourceRepository = resourceRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a new generic Resource under the current tenant.
     *
     * @throws ConflictException if a Resource with the same compound key
     *                           {@code (clientId, resourceType, externalId)} already exists
     */
    public ResourceResponse createResource(CreateResourceRequest request) {
        UUID clientId = TenantContext.get();

        if (resourceRepository.existsByClientIdAndResourceTypeAndExternalId(
                clientId, request.getResourceType(), request.getExternalId())) {
            throw new ConflictException(
                    "Resource with resourceType '" + request.getResourceType()
                            + "' and externalId '" + request.getExternalId() + "' already exists");
        }

        String attributesJson = serializeAttributes(request.getAttributes());

        Resource resource = Resource.builder()
                .clientId(clientId)
                .resourceType(request.getResourceType())
                .externalId(request.getExternalId())
                .attributes(attributesJson)
                .createdAt(Instant.now())
                .build();

        resource = resourceRepository.save(resource);
        return toResponse(resource);
    }

    /**
     * Retrieves a Resource by its primary UUID, scoped to the current tenant.
     *
     * @throws NotFoundException if not found or belongs to another tenant
     */
    public ResourceResponse getResourceById(UUID resourceId) {
        UUID clientId = TenantContext.get();
        Resource resource = resourceRepository.findByClientIdAndId(clientId, resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found"));
        return toResponse(resource);
    }

    /**
     * Retrieves a Resource by its composite identifier {@code (resourceType, externalId)},
     * scoped to the current tenant.
     *
     * @throws NotFoundException if not found or belongs to another tenant
     */
    public ResourceResponse getResourceByTypeAndExternalId(String resourceType, String externalId) {
        UUID clientId = TenantContext.get();
        Resource resource = resourceRepository.findByClientIdAndResourceTypeAndExternalId(
                clientId, resourceType, externalId)
                .orElseThrow(() -> new NotFoundException("Resource not found"));
        return toResponse(resource);
    }

    /**
     * Lists all Resources belonging to the current tenant, optionally filtered by resourceType.
     *
     * @param resourceType optional type filter; if null or blank, all tenant resources are returned
     */
    public List<ResourceResponse> listResources(String resourceType) {
        UUID clientId = TenantContext.get();
        List<Resource> resources;
        if (resourceType != null && !resourceType.isBlank()) {
            resources = resourceRepository.findAllByClientIdAndResourceType(clientId, resourceType);
        } else {
            resources = resourceRepository.findAllByClientId(clientId);
        }
        return resources.stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Replaces the ABAC attributes for a Resource (full replacement).
     *
     * @throws NotFoundException if not found or belongs to another tenant
     */
    public ResourceResponse updateAttributes(UUID resourceId, Map<String, Object> newAttributes) {
        UUID clientId = TenantContext.get();
        Resource resource = resourceRepository.findByClientIdAndId(clientId, resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found"));

        resource.setAttributes(serializeAttributes(newAttributes));
        resource = resourceRepository.save(resource);
        return toResponse(resource);
    }

    /**
     * Deletes a Resource by its internal UUID, scoped to the current tenant.
     *
     * @throws NotFoundException if not found or belongs to another tenant
     */
    public void deleteResource(UUID resourceId) {
        UUID clientId = TenantContext.get();
        Resource resource = resourceRepository.findByClientIdAndId(clientId, resourceId)
                .orElseThrow(() -> new NotFoundException("Resource not found"));
        resourceRepository.delete(resource);
    }

    // =========================================================================
    // Mapping & Serialization Helpers
    // =========================================================================

    private ResourceResponse toResponse(Resource resource) {
        return new ResourceResponse(
                resource.getId(),
                resource.getClientId(),
                resource.getResourceType(),
                resource.getExternalId(),
                deserializeAttributes(resource.getAttributes()),
                resource.getCreatedAt()
        );
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

    private Map<String, Object> deserializeAttributes(String json) {
        if (json == null || json.isBlank() || "{}".equals(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE_REF);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Failed to deserialize attributes", e);
        }
    }
}
