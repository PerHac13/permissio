package com.perhac.permissio.policy.controller;

import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.authentication.dto.RegisterRequest;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.policy.dto.CreatePolicyRequest;
import com.perhac.permissio.policy.entity.PolicyType;
import com.perhac.permissio.policy.repository.PolicyRepository;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class PolicyControllerIntegrationTest {

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

    private static final String RAW_API_KEY_A = "policy-key-a";
    private static final String RAW_API_KEY_B = "policy-key-b";

    private Client tenantA;
    private String jwtTokenA;
    private String jwtTokenB;

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

        clientRepository.save(Client.builder()
                .name("Tenant B")
                .apiKeyHash(apiKeyHasher.hash(RAW_API_KEY_B))
                .createdAt(Instant.now())
                .build());

        jwtTokenA = obtainJwt(RAW_API_KEY_A, "alice", "pwd123");
        jwtTokenB = obtainJwt(RAW_API_KEY_B, "bob", "pwd123");
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

    @Test
    @DisplayName("POST /api/v1/policies creates a policy successfully")
    void createPolicy_returns201() throws Exception {
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .resourceType("document")
                .action(Action.UPDATE)
                .policyType(PolicyType.ABAC)
                .expression("#subject['dept'] == #resource['dept']")
                .build();

        mockMvc.perform(post("/api/v1/policies")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.clientId", is(tenantA.getId().toString())))
                .andExpect(jsonPath("$.resourceType", is("document")))
                .andExpect(jsonPath("$.action", is("UPDATE")))
                .andExpect(jsonPath("$.policyType", is("ABAC")))
                .andExpect(jsonPath("$.expression", is("#subject['dept'] == #resource['dept']")));
    }

    @Test
    @DisplayName("GET /api/v1/policies/{id} retrieves policy and enforces tenant isolation")
    void getPolicy_returns200AndEnforcesIsolation() throws Exception {
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .resourceType("document")
                .action(Action.READ)
                .policyType(PolicyType.ABAC)
                .expression("#subject['clearance'] >= 3")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/policies")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String policyId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();

        // Tenant A can get it
        mockMvc.perform(get("/api/v1/policies/{id}", policyId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(policyId)));

        // Tenant B cannot get it (404)
        mockMvc.perform(get("/api/v1/policies/{id}", policyId)
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/v1/policies/{id} deletes policy")
    void deletePolicy_returns204() throws Exception {
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .resourceType("document")
                .action(Action.READ)
                .policyType(PolicyType.ABAC)
                .expression("#subject['clearance'] >= 3")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/policies")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String policyId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();

        mockMvc.perform(delete("/api/v1/policies/{id}", policyId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/policies/{id}", policyId)
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isNotFound());
    }

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

        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }
}
