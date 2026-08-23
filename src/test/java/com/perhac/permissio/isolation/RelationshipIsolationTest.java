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

import static org.hamcrest.Matchers.empty;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 11.3 — Two-client isolation test for <strong>Relationships</strong>.
 * <p>
 * This is the <strong>highest-priority</strong> isolation test because the
 * {@code relationships} table is the ReBAC-critical entity. If cross-tenant
 * leakage occurs here, an attacker could authorize themselves against another
 * tenant's resources.
 * <p>
 * Additionally tests that client-a <strong>cannot create relationships referencing
 * client-b's subjects or resources</strong>.
 */
@SpringBootTest
@ActiveProfiles("test")
class RelationshipIsolationTest {

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

    private static final String API_KEY_A = "isolation-rel-key-a";
    private static final String API_KEY_B = "isolation-rel-key-b";

    private String jwtA;
    private String jwtB;
    private String subjectIdA;
    private String subjectIdB;
    private String resourceIdA;
    private String resourceIdB;

    @BeforeEach
    void setUp() throws Exception {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();

        auditLogRepository.deleteAll();
        policyRepository.deleteAll();
        relationshipRepository.deleteAll();
        resourceRepository.deleteAll();
        subjectRepository.deleteAll();
        clientRepository.deleteAll();

        clientRepository.save(Client.builder()
                .name("Rel Isolation Tenant A")
                .apiKeyHash(apiKeyHasher.hash(API_KEY_A))
                .createdAt(Instant.now())
                .build());
        clientRepository.save(Client.builder()
                .name("Rel Isolation Tenant B")
                .apiKeyHash(apiKeyHasher.hash(API_KEY_B))
                .createdAt(Instant.now())
                .build());

        jwtA = registerAndGetJwt(API_KEY_A, "rel-user-a", "pass123");
        jwtB = registerAndGetJwt(API_KEY_B, "rel-user-b", "pass123");

        subjectIdA = createSubject(API_KEY_A, jwtA, "rel-subject-a");
        subjectIdB = createSubject(API_KEY_B, jwtB, "rel-subject-b");

        resourceIdA = createResource(API_KEY_A, jwtA, "project", "proj-a");
        resourceIdB = createResource(API_KEY_B, jwtB, "project", "proj-b");

        // Create a relationship under each tenant
        createRelationship(API_KEY_A, jwtA, subjectIdA, resourceIdA, "OWNER");
        createRelationship(API_KEY_B, jwtB, subjectIdB, resourceIdB, "MEMBER");
    }

    @Test
    void clientA_cannotSeeRelationshipsOfClientB_bySubject() throws Exception {
        mockMvc.perform(get("/api/v1/relationships")
                        .param("subjectId", subjectIdB)
                        .header("X-API-Key", API_KEY_A)
                        .header("Authorization", "Bearer " + jwtA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void clientB_cannotSeeRelationshipsOfClientA_bySubject() throws Exception {
        mockMvc.perform(get("/api/v1/relationships")
                        .param("subjectId", subjectIdA)
                        .header("X-API-Key", API_KEY_B)
                        .header("Authorization", "Bearer " + jwtB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", empty()));
    }

    @Test
    void clientA_cannotCreateRelationship_withClientBSubject() throws Exception {
        // Client A tries to link client B's subject to client A's resource
        String body = String.format(
                "{\"subjectId\":\"%s\",\"resourceId\":\"%s\",\"relation\":\"MEMBER\"}",
                subjectIdB, resourceIdA);

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", API_KEY_A)
                        .header("Authorization", "Bearer " + jwtA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientA_cannotCreateRelationship_withClientBResource() throws Exception {
        // Client A tries to link client A's subject to client B's resource
        String body = String.format(
                "{\"subjectId\":\"%s\",\"resourceId\":\"%s\",\"relation\":\"MEMBER\"}",
                subjectIdA, resourceIdB);

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", API_KEY_A)
                        .header("Authorization", "Bearer " + jwtA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());
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
