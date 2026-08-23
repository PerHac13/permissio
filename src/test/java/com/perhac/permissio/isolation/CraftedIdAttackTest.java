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

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 11.4 — Crafted-ID attack test.
 * <p>
 * Simulates an attacker who knows (or guesses) a UUID belonging to another tenant.
 * The system MUST return 404 (not 403) to avoid leaking the existence of the entity.
 * <p>
 * Tests cover subjects, resources, and relationships.
 */
@SpringBootTest
@ActiveProfiles("test")
class CraftedIdAttackTest {

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

    private static final String API_KEY_A = "crafted-id-key-a";
    private static final String API_KEY_B = "crafted-id-key-b";

    private String jwtA;
    private String jwtB;
    private String subjectIdB;
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
                .name("Crafted ID Tenant A")
                .apiKeyHash(apiKeyHasher.hash(API_KEY_A))
                .createdAt(Instant.now())
                .build());
        clientRepository.save(Client.builder()
                .name("Crafted ID Tenant B")
                .apiKeyHash(apiKeyHasher.hash(API_KEY_B))
                .createdAt(Instant.now())
                .build());

        jwtA = registerAndGetJwt(API_KEY_A, "attack-user-a", "pass123");
        jwtB = registerAndGetJwt(API_KEY_B, "attack-user-b", "pass123");

        subjectIdB = createSubject(API_KEY_B, jwtB, "victim-subject");
        resourceIdB = createResource(API_KEY_B, jwtB, "secret-doc", "victim-doc");
    }

    /**
     * Client A guesses Client B's subject UUID — must get 404, not 403.
     * The response MUST NOT reveal that the entity exists under another tenant.
     */
    @Test
    void craftedSubjectId_returns404_neverLeaksExistence() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/" + subjectIdB)
                        .header("X-API-Key", API_KEY_A)
                        .header("Authorization", "Bearer " + jwtA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    /**
     * Client A guesses Client B's resource UUID — must get 404, not 403.
     */
    @Test
    void craftedResourceId_returns404_neverLeaksExistence() throws Exception {
        mockMvc.perform(get("/api/v1/resources/" + resourceIdB)
                        .header("X-API-Key", API_KEY_A)
                        .header("Authorization", "Bearer " + jwtA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code", is("NOT_FOUND")));
    }

    /**
     * Client A tries to authorize against Client B's subject+resource — must fail
     * with 404, not with a denial that reveals the entities exist.
     */
    @Test
    void craftedAuthorizeRequest_returns404_neverLeaksExistence() throws Exception {
        String body = String.format(
                "{\"subjectId\":\"%s\",\"resourceId\":\"%s\",\"action\":\"READ\"}",
                subjectIdB, resourceIdB);

        mockMvc.perform(post("/api/v1/authorize")
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
}
