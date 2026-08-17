package com.perhac.permissio.relationship.controller;

import com.perhac.permissio.authentication.dto.RegisterRequest;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.relationship.dto.CreateRelationshipRequest;
import com.perhac.permissio.relationship.entity.Relation;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.dto.CreateResourceRequest;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.security.ApiKeyHasher;
import com.perhac.permissio.subject.repository.SubjectRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full HTTP integration tests for Relationship CRUD endpoints under {@code /api/v1/relationships}.
 * <p>
 * Uses Spring Boot Test with H2 database and full Spring Security filter chain.
 */
@SpringBootTest
@ActiveProfiles("test")
class RelationshipControllerIntegrationTest {

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
    private ApiKeyHasher apiKeyHasher;

    private static final String RAW_API_KEY_A = "rel-test-api-key-a";
    private static final String RAW_API_KEY_B = "rel-test-api-key-b";

    private Client tenantA;
    private Client tenantB;

    private String jwtTokenA;
    private String jwtTokenB;

    private UUID subjectIdA;
    private UUID subjectIdB;
    private UUID resourceIdA1;
    private UUID resourceIdA2;
    private UUID resourceIdB;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

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

        // Register subjects to get JWTs and subject IDs
        jwtTokenA = obtainJwtAndSubjectId(RAW_API_KEY_A, "alice", "password123", true);
        jwtTokenB = obtainJwtAndSubjectId(RAW_API_KEY_B, "bob", "password123", false);

        // Create resources under Tenant A and Tenant B
        resourceIdA1 = UUID.fromString(createResource(RAW_API_KEY_A, jwtTokenA, "document", "doc-1"));
        resourceIdA2 = UUID.fromString(createResource(RAW_API_KEY_A, jwtTokenA, "document", "doc-2"));
        resourceIdB = UUID.fromString(createResource(RAW_API_KEY_B, jwtTokenB, "document", "doc-b1"));
    }

    @AfterEach
    void tearDown() {
        relationshipRepository.deleteAll();
        resourceRepository.deleteAll();
        subjectRepository.deleteAll();
        clientRepository.deleteAll();
    }

    // =========================================================================
    // 1. POST /api/v1/relationships — 201 Created
    // =========================================================================

    @Test
    void createRelationship_returns201WithRelationshipResponse() throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA1)
                .relation(Relation.OWNER)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.clientId", is(tenantA.getId().toString())))
                .andExpect(jsonPath("$.subjectId", is(subjectIdA.toString())))
                .andExpect(jsonPath("$.resourceId", is(resourceIdA1.toString())))
                .andExpect(jsonPath("$.relation", is("OWNER")))
                .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    // =========================================================================
    // 2. POST /api/v1/relationships — Validation (400 Bad Request)
    // =========================================================================

    @Test
    void createRelationship_missingSubjectId_returns400() throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .resourceId(resourceIdA1)
                .relation(Relation.OWNER)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRelationship_missingResourceId_returns400() throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectIdA)
                .relation(Relation.OWNER)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRelationship_missingRelation_returns400() throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA1)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 3. POST /api/v1/relationships — Cross-tenant & Conflict
    // =========================================================================

    @Test
    void createRelationship_crossTenantSubject_returns404() throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectIdB)
                .resourceId(resourceIdA1)
                .relation(Relation.OWNER)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Subject not found")));
    }

    @Test
    void createRelationship_crossTenantResource_returns404() throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdB)
                .relation(Relation.OWNER)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Resource not found")));
    }

    @Test
    void createRelationship_duplicateTuple_returns409() throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA1)
                .relation(Relation.OWNER)
                .build();

        // First creation succeeds
        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second creation with identical tuple fails
        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code", is("CONFLICT")))
                .andExpect(jsonPath("$.message", is("Relationship tuple already exists")));
    }

    // =========================================================================
    // 4. GET /api/v1/relationships/{id} — 200 OK and 404 Not Found
    // =========================================================================

    @Test
    void getRelationshipById_returns200() throws Exception {
        String relId = createRelationshipAndReturnId(subjectIdA, resourceIdA1, Relation.MANAGER);

        mockMvc.perform(get("/api/v1/relationships/{id}", relId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(relId)))
                .andExpect(jsonPath("$.relation", is("MANAGER")))
                .andExpect(jsonPath("$.subjectId", is(subjectIdA.toString())))
                .andExpect(jsonPath("$.resourceId", is(resourceIdA1.toString())));
    }

    @Test
    void getRelationshipById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/relationships/{id}", UUID.randomUUID())
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    // =========================================================================
    // 5. GET /api/v1/relationships — Listing & Filtering
    // =========================================================================

    @Test
    void listRelationships_all_returns200WithTenantRelationships() throws Exception {
        createRelationshipAndReturnId(subjectIdA, resourceIdA1, Relation.OWNER);
        createRelationshipAndReturnId(subjectIdA, resourceIdA2, Relation.MEMBER);

        mockMvc.perform(get("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listRelationships_filteredBySubjectId_returns200() throws Exception {
        createRelationshipAndReturnId(subjectIdA, resourceIdA1, Relation.OWNER);
        createRelationshipAndReturnId(subjectIdA, resourceIdA2, Relation.MEMBER);

        mockMvc.perform(get("/api/v1/relationships")
                        .param("subjectId", subjectIdA.toString())
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void listRelationships_filteredByResourceId_returns200() throws Exception {
        createRelationshipAndReturnId(subjectIdA, resourceIdA1, Relation.OWNER);
        createRelationshipAndReturnId(subjectIdA, resourceIdA2, Relation.MEMBER);

        mockMvc.perform(get("/api/v1/relationships")
                        .param("resourceId", resourceIdA1.toString())
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].relation", is("OWNER")));
    }

    @Test
    void listRelationships_filteredBySubjectIdAndResourceId_returns200() throws Exception {
        createRelationshipAndReturnId(subjectIdA, resourceIdA1, Relation.OWNER);
        createRelationshipAndReturnId(subjectIdA, resourceIdA2, Relation.MEMBER);

        mockMvc.perform(get("/api/v1/relationships")
                        .param("subjectId", subjectIdA.toString())
                        .param("resourceId", resourceIdA2.toString())
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].relation", is("MEMBER")));
    }

    // =========================================================================
    // 6. DELETE /api/v1/relationships/{id} — 204 No Content and 404 Not Found
    // =========================================================================

    @Test
    void deleteRelationship_returns204() throws Exception {
        String relId = createRelationshipAndReturnId(subjectIdA, resourceIdA1, Relation.OWNER);

        mockMvc.perform(delete("/api/v1/relationships/{id}", relId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNoContent());

        // Subsequent get returns 404
        mockMvc.perform(get("/api/v1/relationships/{id}", relId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRelationship_notFound_returns404() throws Exception {
        mockMvc.perform(delete("/api/v1/relationships/{id}", UUID.randomUUID())
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    // =========================================================================
    // 7. Cross-Tenant Isolation
    // =========================================================================

    @Test
    void crossTenantIsolation_cannotReadOtherTenantsRelationship() throws Exception {
        // Create relationship under Tenant B
        CreateRelationshipRequest bRequest = CreateRelationshipRequest.builder()
                .subjectId(subjectIdB)
                .resourceId(resourceIdB)
                .relation(Relation.OWNER)
                .build();

        MvcResult bResult = mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String tenantBRelId = objectMapper.readTree(bResult.getResponse().getContentAsString()).get("id").asText();

        // Tenant A tries to access Tenant B's relationship — returns 404
        mockMvc.perform(get("/api/v1/relationships/{id}", tenantBRelId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound());
    }

    @Test
    void crossTenantIsolation_cannotDeleteOtherTenantsRelationship() throws Exception {
        CreateRelationshipRequest bRequest = CreateRelationshipRequest.builder()
                .subjectId(subjectIdB)
                .resourceId(resourceIdB)
                .relation(Relation.OWNER)
                .build();

        MvcResult bResult = mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String tenantBRelId = objectMapper.readTree(bResult.getResponse().getContentAsString()).get("id").asText();

        // Tenant A tries to delete Tenant B's relationship — returns 404
        mockMvc.perform(delete("/api/v1/relationships/{id}", tenantBRelId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound());
    }

    // =========================================================================
    // 8. Security — Missing/Invalid Auth
    // =========================================================================

    @Test
    void missingApiKey_returns401() throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA1)
                .relation(Relation.OWNER)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void missingJwt_returns403() throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA1)
                .relation(Relation.OWNER)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void crossTenantJwtReplay_returns401() throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA1)
                .relation(Relation.OWNER)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private String obtainJwtAndSubjectId(String apiKey, String externalId, String password, boolean isTenantA) throws Exception {
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

        String content = result.getResponse().getContentAsString();
        String token = objectMapper.readTree(content).get("token").asText();
        UUID subId = UUID.fromString(objectMapper.readTree(content).get("subjectId").asText());

        if (isTenantA) {
            this.subjectIdA = subId;
        } else {
            this.subjectIdB = subId;
        }

        return token;
    }

    private String createResource(String apiKey, String jwt, String resourceType, String externalId) throws Exception {
        CreateResourceRequest request = CreateResourceRequest.builder()
                .resourceType(resourceType)
                .externalId(externalId)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", apiKey)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    private String createRelationshipAndReturnId(UUID subjectId, UUID resourceId, Relation relation) throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(relation)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }
}
