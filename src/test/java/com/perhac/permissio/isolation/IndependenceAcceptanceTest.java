package com.perhac.permissio.isolation;

import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.policy.repository.PolicyRepository;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 11.5 — Independence acceptance test (PRD 2.3 / TRD 9.4).
 * <p>
 * Proves that Permissio boots with an empty database, registers a fictitious client,
 * and completes a full authorization flow with <strong>zero external-service dependencies</strong>.
 * <p>
 * This is the canonical "it works out of the box" test — the entire stack (registration,
 * subject CRUD, resource CRUD, relationship creation, policy evaluation, and the
 * {@code POST /authorize} endpoint) is exercised end-to-end.
 */
@SpringBootTest
@ActiveProfiles("test")
class IndependenceAcceptanceTest {

    @Autowired private WebApplicationContext wac;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ClientRepository clientRepository;
    @Autowired private SubjectRepository subjectRepository;
    @Autowired private ResourceRepository resourceRepository;
    @Autowired private RelationshipRepository relationshipRepository;
    @Autowired private PolicyRepository policyRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private ApiKeyHasher apiKeyHasher;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();

        auditLogRepository.deleteAll();
        policyRepository.deleteAll();
        relationshipRepository.deleteAll();
        resourceRepository.deleteAll();
        subjectRepository.deleteAll();
        clientRepository.deleteAll();
    }

    /**
     * Full end-to-end flow — no external services, no pre-existing data.
     * <ol>
     *   <li>Register a fictitious client (directly via repository — simulates admin provisioning)</li>
     *   <li>Register a subject via {@code POST /auth/register}</li>
     *   <li>Create a resource via {@code POST /resources}</li>
     *   <li>Create a relationship (OWNER) via {@code POST /relationships}</li>
     *   <li>Call {@code POST /authorize} — expect ALLOWED</li>
     *   <li>Call {@code POST /authorize} with insufficient relation — expect DENIED</li>
     *   <li>Verify health endpoint is accessible</li>
     * </ol>
     */
    @Test
    void fullFlowWithZeroExternalDependencies() throws Exception {
        String apiKey = "independence-test-key";

        // 1. Provision client
        clientRepository.save(Client.builder()
                .name("Independence Test Client")
                .apiKeyHash(apiKeyHasher.hash(apiKey))
                .createdAt(Instant.now())
                .build());

        // 2. Register subject and obtain JWT
        String jwt = registerAndGetJwt(apiKey, "test-user", "password123");
        assertThat(jwt).isNotBlank();

        // 3. Create a resource
        String resourceId = createResource(apiKey, jwt, "document", "doc-001");
        assertThat(resourceId).isNotBlank();

        // 4. Create a subject for the relationship
        String subjectId = createSubject(apiKey, jwt, "authz-subject");
        assertThat(subjectId).isNotBlank();

        // 5. Create an OWNER relationship
        createRelationship(apiKey, jwt, subjectId, resourceId, "OWNER");

        // 6. Authorize — OWNER can READ → ALLOWED
        String authorizeBody = String.format(
                "{\"subjectId\":\"%s\",\"resourceId\":\"%s\",\"action\":\"READ\"}",
                subjectId, resourceId);

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", apiKey)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(authorizeBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)));

        // 7. Create a MEMBER subject — MEMBER cannot DELETE
        String memberId = createSubject(apiKey, jwt, "member-user");
        createRelationship(apiKey, jwt, memberId, resourceId, "MEMBER");

        String denyBody = String.format(
                "{\"subjectId\":\"%s\",\"resourceId\":\"%s\",\"action\":\"DELETE\"}",
                memberId, resourceId);

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", apiKey)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(denyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.reason", notNullValue()));

        // 8. Health endpoint accessible without auth
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
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

    private void createRelationship(String apiKey, String jwt, String subjectId, String resourceId, String relation) throws Exception {
        String body = String.format(
                "{\"subjectId\":\"%s\",\"resourceId\":\"%s\",\"relation\":\"%s\"}",
                subjectId, resourceId, relation);
        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", apiKey)
                        .header("Authorization", "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }
}
