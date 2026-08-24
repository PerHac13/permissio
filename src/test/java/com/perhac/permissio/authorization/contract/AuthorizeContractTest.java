package com.perhac.permissio.authorization.contract;

import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.authorization.dto.AuthorizeRequest;
import com.perhac.permissio.authorization.dto.AuthorizeResponse;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.policy.repository.PolicyRepository;
import com.perhac.permissio.relationship.entity.Relation;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.security.ApiKeyHasher;
import com.perhac.permissio.subject.repository.SubjectRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 12.2 — Contract Test locking {@code POST /api/v1/authorize} request/response shape.
 * <p>
 * This test guarantees backward-compatibility across Phase 1 and Phase 2 (Zanzibar migration).
 * It fails immediately if:
 * <ul>
 *   <li>Any expected request field is renamed, deleted, or altered in type</li>
 *   <li>Any expected response field is renamed, deleted, or altered in type</li>
 *   <li>JSON schema contracts deviate from TRD Section 6.4 specifications</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthorizeContractTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

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

    private MockMvc mockMvc;

    private static final String RAW_API_KEY = "contract-test-api-key";
    private String jwtToken;
    private String subjectId;
    private String resourceId;

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
                .name("Contract Tenant")
                .apiKeyHash(apiKeyHasher.hash(RAW_API_KEY))
                .createdAt(Instant.now())
                .build());

        jwtToken = registerAndGetJwt(RAW_API_KEY, "contract-user", "password123");
        subjectId = createSubject(RAW_API_KEY, jwtToken, "sub-contract");
        resourceId = createResource(RAW_API_KEY, jwtToken, "document", "doc-contract");
        createRelationship(RAW_API_KEY, jwtToken, subjectId, resourceId, Relation.OWNER.name());
    }

    @Test
    @DisplayName("AuthorizeRequest DTO contract: exact field serialization/deserialization")
    @SuppressWarnings("unchecked")
    void authorizeRequest_contractFieldsLocked() throws Exception {
        UUID subId = UUID.randomUUID();
        UUID resId = UUID.randomUUID();
        String json = String.format("{\"subjectId\":\"%s\",\"resourceId\":\"%s\",\"action\":\"UPDATE\"}", subId, resId);

        AuthorizeRequest req = objectMapper.readValue(json, AuthorizeRequest.class);
        assertThat(req.getSubjectId()).isEqualTo(subId);
        assertThat(req.getResourceId()).isEqualTo(resId);
        assertThat(req.getAction()).isEqualTo(Action.UPDATE);

        String serialized = objectMapper.writeValueAsString(req);
        Map<String, Object> map = objectMapper.readValue(serialized, Map.class);
        assertThat(map.keySet()).containsExactlyInAnyOrder("subjectId", "resourceId", "action");
    }

    @Test
    @DisplayName("AuthorizeResponse DTO contract: exact field serialization/deserialization")
    @SuppressWarnings("unchecked")
    void authorizeResponse_contractFieldsLocked() throws Exception {
        AuthorizeResponse resp = new AuthorizeResponse(true, null, "RebacEvaluator");
        String serialized = objectMapper.writeValueAsString(resp);

        Map<String, Object> map = objectMapper.readValue(serialized, Map.class);
        assertThat(map.keySet()).containsExactlyInAnyOrder("allowed", "reason", "evaluator");

        JsonNode node = objectMapper.readTree(serialized);
        assertThat(node.get("allowed").isBoolean()).isTrue();
        assertThat(node.get("allowed").asBoolean()).isTrue();
        assertThat(node.get("reason").isNull()).isTrue();
        assertThat(node.get("evaluator").asString()).isEqualTo("RebacEvaluator");
    }

    @Test
    @DisplayName("POST /api/v1/authorize HTTP contract: exact response shape for ALLOWED")
    void httpAuthorize_allowedResponseShapeLocked() throws Exception {
        String requestJson = String.format(
                "{\"subjectId\":\"%s\",\"resourceId\":\"%s\",\"action\":\"READ\"}",
                subjectId, resourceId);

        MvcResult result = mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").isBoolean())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist())
                .andExpect(jsonPath("$.evaluator").isString())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.hasNonNull("allowed")).isTrue();
        assertThat(root.has("evaluator")).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/authorize HTTP contract: exact response shape for DENIED")
    void httpAuthorize_deniedResponseShapeLocked() throws Exception {
        String nonExistentSub = createSubject(RAW_API_KEY, jwtToken, "no-rel-subject");
        String requestJson = String.format(
                "{\"subjectId\":\"%s\",\"resourceId\":\"%s\",\"action\":\"DELETE\"}",
                nonExistentSub, resourceId);

        MvcResult result = mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").isBoolean())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.reason").isString())
                .andExpect(jsonPath("$.evaluator").isString())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(root.get("allowed").asBoolean()).isFalse();
        assertThat(root.get("reason").asString()).isNotBlank();
        assertThat(root.get("evaluator").asString()).isNotBlank();
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String registerAndGetJwt(String apiKey, String externalId, String password) throws Exception {
        String body = String.format("{\"externalId\":\"%s\",\"password\":\"%s\"}", externalId, password);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    private String createSubject(String apiKey, String jwt, String externalId) throws Exception {
        String body = String.format("{\"externalId\":\"%s\"}", externalId);
        MvcResult result = mockMvc.perform(post("/api/v1/subjects")
                        .header("X-API-Key", apiKey)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
    }

    private String createResource(String apiKey, String jwt, String type, String externalId) throws Exception {
        String body = String.format("{\"resourceType\":\"%s\",\"externalId\":\"%s\"}", type, externalId);
        MvcResult result = mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", apiKey)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();
    }

    private void createRelationship(String apiKey, String jwt, String subId, String resId, String relation) throws Exception {
        String body = String.format(
                "{\"subjectId\":\"%s\",\"resourceId\":\"%s\",\"relation\":\"%s\"}",
                subId, resId, relation);
        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", apiKey)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
