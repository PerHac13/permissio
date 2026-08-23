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
 * Ticket 11.2 — Two-client isolation test for <strong>Resources</strong>.
 * <p>
 * Creates two independent tenants, provisions resources under each,
 * and asserts no cross-tenant data leakage.
 */
@SpringBootTest
@ActiveProfiles("test")
class ResourceIsolationTest {

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

    private static final String API_KEY_A = "isolation-resource-key-a";
    private static final String API_KEY_B = "isolation-resource-key-b";

    private String jwtA;
    private String jwtB;
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
                .name("Resource Isolation Tenant A")
                .apiKeyHash(apiKeyHasher.hash(API_KEY_A))
                .createdAt(Instant.now())
                .build());
        clientRepository.save(Client.builder()
                .name("Resource Isolation Tenant B")
                .apiKeyHash(apiKeyHasher.hash(API_KEY_B))
                .createdAt(Instant.now())
                .build());

        jwtA = registerAndGetJwt(API_KEY_A, "res-user-a", "pass123");
        jwtB = registerAndGetJwt(API_KEY_B, "res-user-b", "pass123");

        resourceIdA = createResource(API_KEY_A, jwtA, "document", "doc-a-1");
        resourceIdB = createResource(API_KEY_B, jwtB, "document", "doc-b-1");
    }

    @Test
    void clientA_cannotListResourcesOfClientB() throws Exception {
        mockMvc.perform(get("/api/v1/resources")
                        .header("X-API-Key", API_KEY_A)
                        .header("Authorization", "Bearer " + jwtA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.externalId == 'doc-b-1')]", empty()));
    }

    @Test
    void clientA_cannotGetResourceOfClientB_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/resources/" + resourceIdB)
                        .header("X-API-Key", API_KEY_A)
                        .header("Authorization", "Bearer " + jwtA))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientB_cannotGetResourceOfClientA_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/resources/" + resourceIdA)
                        .header("X-API-Key", API_KEY_B)
                        .header("Authorization", "Bearer " + jwtB))
                .andExpect(status().isNotFound());
    }

    @Test
    void clientB_cannotListResourcesOfClientA() throws Exception {
        mockMvc.perform(get("/api/v1/resources")
                        .header("X-API-Key", API_KEY_B)
                        .header("Authorization", "Bearer " + jwtB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.externalId == 'doc-a-1')]", empty()));
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
