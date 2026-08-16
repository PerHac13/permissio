package com.perhac.permissio.subject.controller;

import com.perhac.permissio.subject.dto.CreateSubjectRequest;
import com.perhac.permissio.subject.dto.SubjectResponse;
import com.perhac.permissio.subject.dto.UpdateSubjectAttributesRequest;
import com.perhac.permissio.subject.service.SubjectService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for tenant-scoped Subject CRUD operations.
 * <p>
 * All endpoints require a valid {@code X-API-Key} header (tenant resolution)
 * and a {@code Bearer} JWT token (subject authentication).
 * <ul>
 *   <li>{@code POST   /api/v1/subjects}                     — 201 Created</li>
 *   <li>{@code GET    /api/v1/subjects/{id}}                 — 200 OK</li>
 *   <li>{@code GET    /api/v1/subjects/external/{externalId}} — 200 OK</li>
 *   <li>{@code GET    /api/v1/subjects}                      — 200 OK (list)</li>
 *   <li>{@code PUT    /api/v1/subjects/{id}/attributes}      — 200 OK</li>
 *   <li>{@code DELETE /api/v1/subjects/{id}}                 — 204 No Content</li>
 * </ul>
 *
 * @see SubjectService
 */
@RestController
@RequestMapping("/api/v1/subjects")
public class SubjectController {

    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) {
        this.subjectService = subjectService;
    }

    @PostMapping
    public ResponseEntity<SubjectResponse> createSubject(
            @Valid @RequestBody CreateSubjectRequest request) {
        SubjectResponse response = subjectService.createSubject(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubjectResponse> getSubjectById(@PathVariable UUID id) {
        SubjectResponse response = subjectService.getSubjectById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/external/{externalId}")
    public ResponseEntity<SubjectResponse> getSubjectByExternalId(
            @PathVariable String externalId) {
        SubjectResponse response = subjectService.getSubjectByExternalId(externalId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<SubjectResponse>> listSubjects() {
        List<SubjectResponse> response = subjectService.listSubjects();
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/attributes")
    public ResponseEntity<SubjectResponse> updateAttributes(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateSubjectAttributesRequest request) {
        SubjectResponse response = subjectService.updateAttributes(id, request.getAttributes());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubject(@PathVariable UUID id) {
        subjectService.deleteSubject(id);
        return ResponseEntity.noContent().build();
    }
}
