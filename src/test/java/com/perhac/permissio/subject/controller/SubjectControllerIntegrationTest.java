package com.perhac.permissio.subject.controller;

import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.authentication.dto.RegisterRequest;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.policy.repository.PolicyRepository;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.security.ApiKeyHasher;
import com.perhac.permissio.subject.dto.CreateSubjectRequest;
import com.perhac.permissio.subject.dto.UpdateSubjectAttributesRequest;
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
 * Full HTTP integration tests for the Subject CRUD endpoints.
 * <p>
 * Uses a real Spring context with H2 database. Subject management endpoints
 * require both a valid {@code X-API-Key} and a {@code Bearer} JWT token.
 * The JWT is obtained by first registering a subject via the auth endpoints.
 */
@SpringBootTest
@ActiveProfiles("test")
class SubjectControllerIntegrationTest {

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

    private static final String RAW_API_KEY_A = "subject-test-api-key-a";
    private static final String RAW_API_KEY_B = "subject-test-api-key-b";
    private Client tenantA;
    private Client tenantB;

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

        tenantB = clientRepository.save(Client.builder()
                .name("Tenant B")
                .apiKeyHash(apiKeyHasher.hash(RAW_API_KEY_B))
                .createdAt(Instant.now())
                .build());

        // Register a subject via auth endpoint to obtain a valid JWT for tenant A
        jwtTokenA = obtainJwt(RAW_API_KEY_A, "auth-user", "password123");
    }

    // =========================================================================
    // 1. POST /api/v1/subjects — 201 Created
    // =========================================================================

    @Test
    void createSubject_returns201WithSubjectResponse() throws Exception {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .externalId("new-subject")
                .password("pass123")
                .attributes(Map.of("department", "engineering"))
                .build();

        mockMvc.perform(post("/api/v1/subjects")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.clientId", is(tenantA.getId().toString())))
                .andExpect(jsonPath("$.externalId", is("new-subject")))
                .andExpect(jsonPath("$.attributes.department", is("engineering")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    // =========================================================================
    // 2. POST /api/v1/subjects — 400 Bad Request (missing externalId)
    // =========================================================================

    @Test
    void createSubject_missingExternalId_returns400() throws Exception {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .password("pass123")
                .build();

        mockMvc.perform(post("/api/v1/subjects")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 3. POST /api/v1/subjects — 409 Conflict (duplicate externalId)
    // =========================================================================

    @Test
    void createSubject_duplicateExternalId_returns409() throws Exception {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .externalId("dup-subject")
                .password("pass123")
                .build();

        // First creation succeeds
        mockMvc.perform(post("/api/v1/subjects")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Duplicate returns 409
        mockMvc.perform(post("/api/v1/subjects")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already exists")));
    }

    // =========================================================================
    // 4. GET /api/v1/subjects/{id} — 200 OK
    // =========================================================================

    @Test
    void getSubjectById_returns200() throws Exception {
        String subjectId = createSubjectAndReturnId("get-by-id-user");

        mockMvc.perform(get("/api/v1/subjects/{id}", subjectId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(subjectId)))
                .andExpect(jsonPath("$.externalId", is("get-by-id-user")));
    }

    // =========================================================================
    // 5. GET /api/v1/subjects/{id} — 404 Not Found
    // =========================================================================

    @Test
    void getSubjectById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/{id}", "00000000-0000-0000-0000-000000000000")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    // =========================================================================
    // 6. GET /api/v1/subjects/external/{externalId} — 200 OK
    // =========================================================================

    @Test
    void getSubjectByExternalId_returns200() throws Exception {
        createSubjectAndReturnId("ext-lookup-user");

        mockMvc.perform(get("/api/v1/subjects/external/{externalId}", "ext-lookup-user")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.externalId", is("ext-lookup-user")));
    }

    // =========================================================================
    // 7. GET /api/v1/subjects — 200 OK with tenant list
    // =========================================================================

    @Test
    void listSubjects_returns200WithTenantSubjects() throws Exception {
        createSubjectAndReturnId("list-user-1");
        createSubjectAndReturnId("list-user-2");

        mockMvc.perform(get("/api/v1/subjects")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                // auth-user (from setUp) + 2 created here = 3
                .andExpect(jsonPath("$", hasSize(3)));
    }

    // =========================================================================
    // 8. PUT /api/v1/subjects/{id}/attributes — 200 OK
    // =========================================================================

    @Test
    void updateAttributes_returns200WithUpdatedAttributes() throws Exception {
        String subjectId = createSubjectAndReturnId("attr-update-user");

        UpdateSubjectAttributesRequest updateRequest = UpdateSubjectAttributesRequest.builder()
                .attributes(Map.of("role", "manager", "clearance", 5))
                .build();

        mockMvc.perform(put("/api/v1/subjects/{id}/attributes", subjectId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attributes.role", is("manager")))
                .andExpect(jsonPath("$.attributes.clearance", is(5)));
    }

    // =========================================================================
    // 9. DELETE /api/v1/subjects/{id} — 204 No Content
    // =========================================================================

    @Test
    void deleteSubject_returns204() throws Exception {
        String subjectId = createSubjectAndReturnId("delete-user");

        mockMvc.perform(delete("/api/v1/subjects/{id}", subjectId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNoContent());

        // Verify it's actually gone
        mockMvc.perform(get("/api/v1/subjects/{id}", subjectId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 10. Cross-Tenant Isolation
    // =========================================================================

    @Test
    void crossTenantIsolation_cannotReadOtherTenantsSubject() throws Exception {
        // Create subject under tenant A
        String subjectId = createSubjectAndReturnId("isolated-user");

        // Obtain JWT for tenant B
        String jwtTokenB = obtainJwt(RAW_API_KEY_B, "tenant-b-auth-user", "password123");

        // Tenant B cannot read tenant A's subject — returns 404 (not 403)
        mockMvc.perform(get("/api/v1/subjects/{id}", subjectId)
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB))
                .andExpect(status().isNotFound());

        // Tenant B cannot update tenant A's subject
        UpdateSubjectAttributesRequest updateRequest = UpdateSubjectAttributesRequest.builder()
                .attributes(Map.of("hacked", true))
                .build();

        mockMvc.perform(put("/api/v1/subjects/{id}/attributes", subjectId)
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isNotFound());

        // Tenant B cannot delete tenant A's subject
        mockMvc.perform(delete("/api/v1/subjects/{id}", subjectId)
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 11. Security — Missing/Invalid API key
    // =========================================================================

    @Test
    void missingApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/subjects")
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidApiKey_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/subjects")
                        .header("X-API-Key", "invalid-key")
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Registers a subject via the auth endpoint and returns the JWT token.
     */
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
                .get("token").asText();
    }

    /**
     * Creates a subject via the management endpoint and returns its ID.
     */
    private String createSubjectAndReturnId(String externalId) throws Exception {
        CreateSubjectRequest request = CreateSubjectRequest.builder()
                .externalId(externalId)
                .password("pass123")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/subjects")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asText();
    }
}
