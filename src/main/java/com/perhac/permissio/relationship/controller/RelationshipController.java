package com.perhac.permissio.relationship.controller;

import com.perhac.permissio.relationship.dto.CreateRelationshipRequest;
import com.perhac.permissio.relationship.dto.RelationshipResponse;
import com.perhac.permissio.relationship.service.RelationshipService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for tenant-scoped Relationship (ReBAC tuple) CRUD operations.
 * <p>
 * All endpoints require a valid {@code X-API-Key} header (for tenant resolution)
 * and a {@code Bearer} JWT token (for subject authentication).
 * <ul>
 *   <li>{@code POST   /api/v1/relationships}                                       — 201 Created</li>
 *   <li>{@code GET    /api/v1/relationships/{id}}                                  — 200 OK</li>
 *   <li>{@code GET    /api/v1/relationships(?subjectId={}&resourceId={})}          — 200 OK</li>
 *   <li>{@code DELETE /api/v1/relationships/{id}}                                  — 204 No Content</li>
 * </ul>
 *
 * @see RelationshipService
 */
@RestController
@RequestMapping("/api/v1/relationships")
public class RelationshipController {

    private final RelationshipService relationshipService;

    public RelationshipController(RelationshipService relationshipService) {
        this.relationshipService = relationshipService;
    }

    @PostMapping
    public ResponseEntity<RelationshipResponse> createRelationship(
            @Valid @RequestBody CreateRelationshipRequest request) {
        RelationshipResponse response = relationshipService.createRelationship(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RelationshipResponse> getRelationshipById(@PathVariable UUID id) {
        RelationshipResponse response = relationshipService.getRelationshipById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<RelationshipResponse>> listRelationships(
            @RequestParam(name = "subjectId", required = false) UUID subjectId,
            @RequestParam(name = "resourceId", required = false) UUID resourceId) {
        List<RelationshipResponse> response = relationshipService.listRelationships(subjectId, resourceId);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRelationship(@PathVariable UUID id) {
        relationshipService.deleteRelationship(id);
        return ResponseEntity.noContent().build();
    }
}
