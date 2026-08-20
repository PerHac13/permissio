package com.perhac.permissio.resource.controller;

import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.authentication.dto.RegisterRequest;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.policy.repository.PolicyRepository;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.dto.CreateResourceRequest;
import com.perhac.permissio.resource.dto.UpdateResourceAttributesRequest;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.security.ApiKeyHasher;
import com.perhac.permissio.subject.repository.SubjectRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full HTTP integration tests for Resource CRUD and attribute management endpoints.
 * <p>
 * Uses a real Spring context with H2 database and Spring Security filter chain.
 * Resource endpoints require both a valid {@code X-API-Key} and a {@code Bearer} JWT token.
 */
@SpringBootTest
@ActiveProfiles("test")
class ResourceControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private ResourceRepository resourceRepository;

    @Autowired
    private RelationshipRepository relationshipRepository;

    @Autowired
    private PolicyRepository policyRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ApiKeyHasher apiKeyHasher;

    private static final String RAW_API_KEY_A = "resource-test-api-key-a";
    private static final String RAW_API_KEY_B = "resource-test-api-key-b";

    private Client tenantA;

    /** JWT token for an authenticated subject under tenant A */
    private String jwtTokenA;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        auditLogRepository.deleteAll();
        policyRepository.deleteAll();
        relationshipRepository.deleteAll();
        resourceRepository.deleteAll();
        subjectRepository.deleteAll();
        clientRepository.deleteAll();

        tenantA = clientRepository.save(Client.builder()
                .name("Tenant A")
                .apiKeyHash(apiKeyHasher.hash(RAW_API_KEY_A))
                .createdAt(Instant.now())
                .build());

        clientRepository.save(Client.builder()
                .name("Tenant B")
                .apiKeyHash(apiKeyHasher.hash(RAW_API_KEY_B))
                .createdAt(Instant.now())
                .build());

        // Register a subject to obtain a valid JWT for tenant A
        jwtTokenA = obtainJwt(RAW_API_KEY_A, "auth-user-a", "password123");
    }

    // =========================================================================
    // 1. POST /api/v1/resources — 201 Created
    // =========================================================================

    @Test
    void createResource_returns201WithResourceResponse() throws Exception {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .attributes(Map.of("department", "finance", "classification", "confidential"))
                .build();

        mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.clientId", is(tenantA.getId().toString())))
                .andExpect(jsonPath("$.resourceType", is("DOCUMENT")))
                .andExpect(jsonPath("$.externalId", is("doc-101")))
                .andExpect(jsonPath("$.attributes.department", is("finance")))
                .andExpect(jsonPath("$.attributes.classification", is("confidential")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    // =========================================================================
    // 2. POST /api/v1/resources — 400 Bad Request
    // =========================================================================

    @Test
    void createResource_missingResourceType_returns400() throws Exception {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .externalId("doc-101")
                .build();

        mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createResource_missingExternalId_returns400() throws Exception {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .resourceType("DOCUMENT")
                .build();

        mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 3. POST /api/v1/resources — 409 Conflict
    // =========================================================================

    @Test
    void createResource_duplicateCompoundKey_returns409() throws Exception {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .resourceType("DOCUMENT")
                .externalId("doc-101")
                .build();

        // First creation succeeds
        mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate creation returns 409
        mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already exists")));
    }

    @Test
    void createResource_sameExternalIdDifferentType_returns201() throws Exception {
        CreateResourceRequest docReq = CreateResourceRequest.builder()
                .resourceType("DOCUMENT")
                .externalId("item-101")
                .build();

        CreateResourceRequest folderReq = CreateResourceRequest.builder()
                .resourceType("FOLDER")
                .externalId("item-101")
                .build();

        mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(docReq)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(folderReq)))
                .andExpect(status().isCreated());
    }

    // =========================================================================
    // 4. GET /api/v1/resources/{id} — 200 OK and 404 Not Found
    // =========================================================================

    @Test
    void getResourceById_returns200() throws Exception {
        String resourceId = createResourceAndReturnId("DOCUMENT", "get-by-id-doc");

        mockMvc.perform(get("/api/v1/resources/{id}", resourceId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(resourceId)))
                .andExpect(jsonPath("$.resourceType", is("DOCUMENT")))
                .andExpect(jsonPath("$.externalId", is("get-by-id-doc")));
    }

    @Test
    void getResourceById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/resources/{id}", "00000000-0000-0000-0000-000000000000")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    // =========================================================================
    // 5. GET /api/v1/resources/type/{type}/external/{externalId}
    // =========================================================================

    @Test
    void getResourceByTypeAndExternalId_returns200() throws Exception {
        createResourceAndReturnId("DOCUMENT", "type-ext-doc");

        mockMvc.perform(get("/api/v1/resources/type/{resourceType}/external/{externalId}", "DOCUMENT", "type-ext-doc")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType", is("DOCUMENT")))
                .andExpect(jsonPath("$.externalId", is("type-ext-doc")));
    }

    @Test
    void getResourceByTypeAndExternalId_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/resources/type/{resourceType}/external/{externalId}", "DOCUMENT", "nonexistent")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    // =========================================================================
    // 6. GET /api/v1/resources — Listing & Type Filtering
    // =========================================================================

    @Test
    void listResources_all_returns200WithTenantResources() throws Exception {
        createResourceAndReturnId("DOCUMENT", "doc-1");
        createResourceAndReturnId("FOLDER", "folder-1");

        mockMvc.perform(get("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listResources_filteredByType_returns200WithFilteredList() throws Exception {
        createResourceAndReturnId("DOCUMENT", "doc-1");
        createResourceAndReturnId("DOCUMENT", "doc-2");
        createResourceAndReturnId("FOLDER", "folder-1");

        mockMvc.perform(get("/api/v1/resources")
                        .param("type", "DOCUMENT")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].resourceType", is("DOCUMENT")))
                .andExpect(jsonPath("$[1].resourceType", is("DOCUMENT")));
    }

    // =========================================================================
    // 7. PUT /api/v1/resources/{id}/attributes — 200 OK
    // =========================================================================

    @Test
    void updateAttributes_returns200WithUpdatedAttributes() throws Exception {
        String resourceId = createResourceAndReturnId("DOCUMENT", "attr-update-doc");

        UpdateResourceAttributesRequest updateRequest = UpdateResourceAttributesRequest.builder()
                .attributes(Map.of("classification", "top-secret", "version", 3))
                .build();

        mockMvc.perform(put("/api/v1/resources/{id}/attributes", resourceId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.classification", is("top-secret")))
                .andExpect(jsonPath("$.attributes.version", is(3)));
    }

    @Test
    void updateAttributes_nullAttributes_returns400() throws Exception {
        String resourceId = createResourceAndReturnId("DOCUMENT", "null-attr-doc");

        mockMvc.perform(put("/api/v1/resources/{id}/attributes", resourceId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"attributes\": null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateAttributes_notFound_returns404() throws Exception {
        UpdateResourceAttributesRequest updateRequest = UpdateResourceAttributesRequest.builder()
                .attributes(Map.of("k", "v"))
                .build();

        mockMvc.perform(put("/api/v1/resources/{id}/attributes", "00000000-0000-0000-0000-000000000000")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 8. DELETE /api/v1/resources/{id} — 204 No Content
    // =========================================================================

    @Test
    void deleteResource_returns204() throws Exception {
        String resourceId = createResourceAndReturnId("DOCUMENT", "delete-doc");

        mockMvc.perform(delete("/api/v1/resources/{id}", resourceId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNoContent());

        // Verify resource is deleted
        mockMvc.perform(get("/api/v1/resources/{id}", resourceId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteResource_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/resources/{id}", "00000000-0000-0000-0000-000000000000")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 9. Cross-Tenant Isolation
    // =========================================================================

    @Test
    void crossTenantIsolation_cannotReadOrModifyOtherTenantsResource() throws Exception {
        // Create resource under tenant A
        String resourceId = createResourceAndReturnId("DOCUMENT", "isolated-doc");

        // Obtain JWT for tenant B
        String jwtTokenB = obtainJwt(RAW_API_KEY_B, "tenant-b-user", "password123");

        // Tenant B cannot read Tenant A's resource by ID — returns 404
        mockMvc.perform(get("/api/v1/resources/{id}", resourceId)
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB))
                .andExpect(status().isNotFound());

        // Tenant B cannot read Tenant A's resource by type/externalId — returns 404
        mockMvc.perform(get("/api/v1/resources/type/{resourceType}/external/{externalId}", "DOCUMENT", "isolated-doc")
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB))
                .andExpect(status().isNotFound());

        // Tenant B cannot update Tenant A's resource
        UpdateResourceAttributesRequest updateRequest = UpdateResourceAttributesRequest.builder()
                .attributes(Map.of("hacked", true))
                .build();

        mockMvc.perform(put("/api/v1/resources/{id}/attributes", resourceId)
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        // Tenant B cannot delete Tenant A's resource
        mockMvc.perform(delete("/api/v1/resources/{id}", resourceId)
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 10. Security — Missing/Invalid Auth
    // =========================================================================

    @Test
    void missingApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/resources")
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/resources")
                        .header("X-API-Key", "invalid-key")
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwtToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private String obtainJwt(String apiKey, String externalId, String password) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .externalId(externalId)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asString();
    }

    private String createResourceAndReturnId(String resourceType, String externalId) throws Exception {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .resourceType(resourceType)
                .externalId(externalId)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asString();
    }
}
