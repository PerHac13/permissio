package com.perhac.permissio.authorization.controller;

import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.authentication.dto.RegisterRequest;
import com.perhac.permissio.authorization.dto.AuthorizeRequest;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.policy.repository.PolicyRepository;
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
import org.junit.jupiter.api.DisplayName;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end integration tests for {@code POST /api/v1/authorize}.
 * <p>
 * Tests the complete decision pipeline (ReBAC ➔ ABAC ➔ Business Rules)
 * with full security filters, multi-tenant isolation, and validation.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthorizationControllerIntegrationTest {

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

    private static final String RAW_API_KEY_A = "authz-test-api-key-a";
    private static final String RAW_API_KEY_B = "authz-test-api-key-b";

    private Client tenantA;
    private Client tenantB;

    private String jwtTokenA;
    private String jwtTokenB;

    private UUID subjectIdA;
    private UUID subjectIdB;
    private UUID resourceIdA;
    private UUID resourceIdB;

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

        // Register subjects under each tenant
        jwtTokenA = obtainJwtAndSubjectId(RAW_API_KEY_A, "alice", "password123", true);
        jwtTokenB = obtainJwtAndSubjectId(RAW_API_KEY_B, "bob", "password123", false);

        // Create resources under each tenant
        resourceIdA = UUID.fromString(createResource(RAW_API_KEY_A, jwtTokenA, "document", "doc-1"));
        resourceIdB = UUID.fromString(createResource(RAW_API_KEY_B, jwtTokenB, "document", "doc-b1"));
    }

    @AfterEach
    void tearDown() {
        auditLogRepository.deleteAll();
        policyRepository.deleteAll();
        relationshipRepository.deleteAll();
        resourceRepository.deleteAll();
        subjectRepository.deleteAll();
        clientRepository.deleteAll();
    }

    // =========================================================================
    // 1. Core ReBAC Matrix Scenarios (PRD Inheritance Table)
    // =========================================================================

    @Test
    @DisplayName("OWNER relation: permits DELETE and APPROVE (all actions)")
    void authorize_ownerRelation_allowedForDeleteAndApprove() throws Exception {
        createRelationship(RAW_API_KEY_A, jwtTokenA, subjectIdA, resourceIdA, Relation.OWNER);

        AuthorizeRequest deleteReq = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.DELETE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)))
                .andExpect(jsonPath("$.reason", nullValue()))
                .andExpect(jsonPath("$.evaluator", is("ALL_PASSED")));

        AuthorizeRequest approveReq = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.APPROVE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(approveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)))
                .andExpect(jsonPath("$.reason", nullValue()))
                .andExpect(jsonPath("$.evaluator", is("ALL_PASSED")));
    }

    @Test
    @DisplayName("MANAGER relation: permits UPDATE, denies DELETE with RELATION_INSUFFICIENT")
    void authorize_managerRelation_permitsUpdate_deniesDelete() throws Exception {
        createRelationship(RAW_API_KEY_A, jwtTokenA, subjectIdA, resourceIdA, Relation.MANAGER);

        // Allowed: UPDATE
        AuthorizeRequest updateReq = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.UPDATE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)))
                .andExpect(jsonPath("$.evaluator", is("ALL_PASSED")));

        // Denied: DELETE
        AuthorizeRequest deleteReq = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.DELETE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.reason", is("RELATION_INSUFFICIENT")))
                .andExpect(jsonPath("$.evaluator", is("REBAC")));
    }

    @Test
    @DisplayName("LEAD relation: permits CREATE, denies UPDATE with RELATION_INSUFFICIENT")
    void authorize_leadRelation_permitsCreate_deniesUpdate() throws Exception {
        createRelationship(RAW_API_KEY_A, jwtTokenA, subjectIdA, resourceIdA, Relation.LEAD);

        // Allowed: CREATE
        AuthorizeRequest createReq = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.CREATE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)))
                .andExpect(jsonPath("$.evaluator", is("ALL_PASSED")));

        // Denied: UPDATE
        AuthorizeRequest updateReq = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.UPDATE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.reason", is("RELATION_INSUFFICIENT")))
                .andExpect(jsonPath("$.evaluator", is("REBAC")));
    }

    @Test
    @DisplayName("MEMBER relation: permits READ, denies UPDATE with RELATION_INSUFFICIENT")
    void authorize_memberRelation_permitsRead_deniesUpdate() throws Exception {
        createRelationship(RAW_API_KEY_A, jwtTokenA, subjectIdA, resourceIdA, Relation.MEMBER);

        // Allowed: READ
        AuthorizeRequest readReq = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.READ)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(readReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)))
                .andExpect(jsonPath("$.evaluator", is("ALL_PASSED")));

        // Denied: UPDATE
        AuthorizeRequest updateReq = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.UPDATE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.reason", is("RELATION_INSUFFICIENT")))
                .andExpect(jsonPath("$.evaluator", is("REBAC")));
    }

    @Test
    @DisplayName("No relationship: denies with NO_RELATIONSHIP")
    void authorize_noRelationship_returnsDeniedWithNoRelationshipReason() throws Exception {
        AuthorizeRequest request = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.READ)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.reason", is("NO_RELATIONSHIP")))
                .andExpect(jsonPath("$.evaluator", is("REBAC")));
    }

    // =========================================================================
    // 2. Validation Failures (400 Bad Request)
    // =========================================================================

    @Test
    void authorize_missingSubjectId_returns400() throws Exception {
        AuthorizeRequest request = AuthorizeRequest.builder()
                .resourceId(resourceIdA)
                .action(Action.READ)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorize_missingResourceId_returns400() throws Exception {
        AuthorizeRequest request = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .action(Action.READ)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void authorize_missingAction_returns400() throws Exception {
        AuthorizeRequest request = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // 3. Cross-Tenant Entity Isolation (404 Not Found)
    // =========================================================================

    @Test
    void authorize_crossTenantSubject_returns404() throws Exception {
        AuthorizeRequest request = AuthorizeRequest.builder()
                .subjectId(subjectIdB) // Belongs to Tenant B
                .resourceId(resourceIdA)
                .action(Action.READ)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Subject not found")));
    }

    @Test
    void authorize_crossTenantResource_returns404() throws Exception {
        AuthorizeRequest request = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdB) // Belongs to Tenant B
                .action(Action.READ)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")))
                .andExpect(jsonPath("$.message", is("Resource not found")));
    }

    // =========================================================================
    // 4. Security & Authentication Checks
    // =========================================================================

    @Test
    void authorize_missingApiKey_returns401() throws Exception {
        AuthorizeRequest request = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.READ)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authorize_missingJwt_returns403() throws Exception {
        AuthorizeRequest request = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.READ)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void authorize_crossTenantJwtReplay_returns401() throws Exception {
        AuthorizeRequest request = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.READ)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenB) // Token issued for Tenant B
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    // =========================================================================
    // 5. Latency Smoke Test (Ticket 6.7)
    // =========================================================================

    @Test
    @DisplayName("Latency smoke test: /authorize decision completes well under 150ms locally")
    void authorize_decisionLatency_completesUnder150ms() throws Exception {
        createRelationship(RAW_API_KEY_A, jwtTokenA, subjectIdA, resourceIdA, Relation.MANAGER);

        AuthorizeRequest request = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.UPDATE)
                .build();

        long startTime = System.nanoTime();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)));

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        assertThat(durationMs).isLessThan(150);
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

    private void createRelationship(String apiKey, String jwt, UUID subjectId, UUID resourceId, Relation relation) throws Exception {
        CreateRelationshipRequest request = CreateRelationshipRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(relation)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", apiKey)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
