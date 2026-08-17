package com.perhac.permissio.resource.controller;

import com.perhac.permissio.resource.dto.CreateResourceRequest;
import com.perhac.permissio.resource.dto.ResourceResponse;
import com.perhac.permissio.resource.dto.UpdateResourceAttributesRequest;
import com.perhac.permissio.resource.service.ResourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for tenant-scoped Resource CRUD operations and attribute management.
 * <p>
 * All endpoints require a valid {@code X-API-Key} header (for tenant resolution)
 * and a {@code Bearer} JWT token (for subject authentication).
 * <ul>
 *   <li>{@code POST   /api/v1/resources}                                            — 201 Created</li>
 *   <li>{@code GET    /api/v1/resources/{id}}                                       — 200 OK</li>
 *   <li>{@code GET    /api/v1/resources/type/{resourceType}/external/{externalId}}   — 200 OK</li>
 *   <li>{@code GET    /api/v1/resources(?type={type})}                              — 200 OK (list/filter)</li>
 *   <li>{@code PUT    /api/v1/resources/{id}/attributes}                            — 200 OK</li>
 *   <li>{@code DELETE /api/v1/resources/{id}}                                       — 204 No Content</li>
 * </ul>
 *
 * @see ResourceService
 */
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceController {

    private final ResourceService resourceService;

    public ResourceController(ResourceService resourceService) {
        this.resourceService = resourceService;
    }

    @PostMapping
    public ResponseEntity<ResourceResponse> createResource(
            @Valid @RequestBody CreateResourceRequest request) {
        ResourceResponse response = resourceService.createResource(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResourceResponse> getResourceById(@PathVariable UUID id) {
        ResourceResponse response = resourceService.getResourceById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/type/{resourceType}/external/{externalId}")
    public ResponseEntity<ResourceResponse> getResourceByTypeAndExternalId(
            @PathVariable String resourceType,
            @PathVariable String externalId) {
        ResourceResponse response = resourceService.getResourceByTypeAndExternalId(resourceType, externalId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<ResourceResponse>> listResources(
            @RequestParam(name = "type", required = false) String type) {
        List<ResourceResponse> response = resourceService.listResources(type);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/attributes")
    public ResponseEntity<ResourceResponse> updateAttributes(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateResourceAttributesRequest request) {
        ResourceResponse response = resourceService.updateAttributes(id, request.getAttributes());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteResource(@PathVariable UUID id) {
        resourceService.deleteResource(id);
        return ResponseEntity.noContent().build();
    }
}
