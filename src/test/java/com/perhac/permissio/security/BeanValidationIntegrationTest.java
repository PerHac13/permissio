package com.perhac.permissio.security;

import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.policy.repository.PolicyRepository;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.repository.ResourceRepository;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 10.2 — Integration tests asserting that Bean Validation annotations
 * on all request DTOs produce 400 + structured {@code ErrorResponse} when
 * malformed payloads are submitted.
 */
@SpringBootTest
@ActiveProfiles("test")
class BeanValidationIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired private ClientRepository clientRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private RelationshipRepository relationshipRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ApiKeyHasher apiKeyHasher;

    private static final String RAW_API_KEY = "validation-test-api-key";
    private String jwtToken;

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

        clientRepository.save(Client.builder()
                .name("Validation Test Tenant")
                .apiKeyHash(apiKeyHasher.hash(RAW_API_KEY))
                .createdAt(Instant.now())
                .build());

        // Obtain a JWT for protected endpoints
        jwtToken = obtainJwt(RAW_API_KEY, "validation-user", "password123");
    }

    // =========================================================================
    // POST /api/v1/auth/register — missing externalId
    // =========================================================================

    @Test
    void register_missingExternalId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-API-Key", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    void register_blankExternalId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-API-Key", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"\",\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    void register_shortPassword_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-API-Key", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"user1\",\"password\":\"ab\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    // =========================================================================
    // POST /api/v1/auth/login — missing fields
    // =========================================================================

    @Test
    void login_missingExternalId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .header("X-API-Key", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"secret123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    // =========================================================================
    // POST /api/v1/subjects — missing externalId
    // =========================================================================

    @Test
    void createSubject_missingExternalId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/subjects")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"pass123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    // =========================================================================
    // POST /api/v1/resources — missing fields
    // =========================================================================

    @Test
    void createResource_missingResourceType_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"externalId\":\"res-1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    void createResource_missingExternalId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceType\":\"document\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    // =========================================================================
    // POST /api/v1/relationships — missing fields
    // =========================================================================

    @Test
    void createRelationship_missingSubjectId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":\"00000000-0000-0000-0000-000000000001\",\"relation\":\"MEMBER\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    void createRelationship_missingRelation_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"00000000-0000-0000-0000-000000000001\",\"resourceId\":\"00000000-0000-0000-0000-000000000002\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    // =========================================================================
    // POST /api/v1/authorize — missing fields
    // =========================================================================

    @Test
    void authorize_missingSubjectId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resourceId\":\"00000000-0000-0000-0000-000000000001\",\"action\":\"READ\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    @Test
    void authorize_missingAction_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectId\":\"00000000-0000-0000-0000-000000000001\",\"resourceId\":\"00000000-0000-0000-0000-000000000002\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("VALIDATION_ERROR")));
    }

    // =========================================================================
    // Malformed JSON body
    // =========================================================================

    @Test
    void malformedJson_returns400_withMalformedRequestCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-API-Key", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", is("MALFORMED_REQUEST")));
    }

    // =========================================================================
    // Helper — obtain JWT via register endpoint
    // =========================================================================

    private String obtainJwt(String apiKey, String externalId, String password) throws Exception {
        String registerBody = String.format(
                "{\"externalId\":\"%s\",\"password\":\"%s\"}", externalId, password);

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token").asString();
    }
}
