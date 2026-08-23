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
 * Ticket 11.1 — Two-client isolation test for <strong>Subjects</strong>.
 * <p>
 * Creates two independent tenants (client-a, client-b), provisions subjects under each,
 * and asserts that client-a's API key can never list, fetch, or see subjects belonging
 * to client-b, and vice versa.
 */
@SpringBootTest
@ActiveProfiles("test")
class SubjectIsolationTest {

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

    private static final String API_KEY_A = "isolation-subject-key-a";
    private static final String API_KEY_B = "isolation-subject-key-b";

    private String jwtA;
    private String jwtB;
    private String subjectIdA;
    private String subjectIdB;

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
                .name("Isolation Tenant A")
                .apiKeyHash(apiKeyHasher.hash(API_KEY_A))
                .createdAt(Instant.now())
                .build());
        clientRepository.save(Client.builder()
                .name("Isolation Tenant B")
                .apiKeyHash(apiKeyHasher.hash(API_KEY_B))
                .createdAt(Instant.now())
                .build());

        jwtA = registerAndGetJwt(API_KEY_A, "user-a", "pass123");
        jwtB = registerAndGetJwt(API_KEY_B, "user-b", "pass123");

        subjectIdA = createSubject(API_KEY_A, jwtA, "subject-of-a");
        subjectIdB = createSubject(API_KEY_B, jwtB, "subject-of-b");
    }

    @Test
    void clientA_cannotListSubjectsOfClientB() throws Exception {
        // Client A lists subjects — should only see its own
        mockMvc.perform(get("/api/v1/subjects")
                        .header("X-API-Key", API_KEY_A)
                        .header("Authorization", "Bearer " + jwtA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.externalId == 'subject-of-b')]", empty()));
    }

    @Test
    void clientA_cannotGetSubjectOfClientB_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/" + subjectIdB)
                        .header("X-API-Key", API_KEY_A)
                        .header("Authorization", "Bearer " + jwtA))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientB_cannotGetSubjectOfClientA_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/subjects/" + subjectIdA)
                        .header("X-API-Key", API_KEY_B)
                        .header("Authorization", "Bearer " + jwtB))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientB_cannotListSubjectsOfClientA() throws Exception {
        mockMvc.perform(get("/api/v1/subjects")
                        .header("X-API-Key", API_KEY_B)
                        .header("Authorization", "Bearer " + jwtB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.externalId == 'subject-of-a')]", empty()));
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
}
