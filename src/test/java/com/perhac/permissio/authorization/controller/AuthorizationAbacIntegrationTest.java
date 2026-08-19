package com.perhac.permissio.authorization.controller;

import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.authentication.dto.RegisterRequest;
import com.perhac.permissio.authorization.dto.AuthorizeRequest;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.policy.dto.CreatePolicyRequest;
import com.perhac.permissio.policy.entity.PolicyType;
import com.perhac.permissio.policy.repository.PolicyRepository;
import com.perhac.permissio.relationship.dto.CreateRelationshipRequest;
import com.perhac.permissio.relationship.entity.Relation;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.dto.CreateResourceRequest;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.security.ApiKeyHasher;
import com.perhac.permissio.subject.dto.UpdateSubjectAttributesRequest;
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
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuthorizationAbacIntegrationTest {

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

    private static final String RAW_API_KEY = "abac-test-key";
    private Client tenant;
    private String jwtToken;
    private UUID subjectId;
    private UUID resourceId;

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

        tenant = clientRepository.save(Client.builder()
                .name("Acme Secure")
                .apiKeyHash(apiKeyHasher.hash(RAW_API_KEY))
                .createdAt(Instant.now())
                .build());

        // Register user alice
        jwtToken = registerSubjectAndGetJwt("alice", "pwd123");

        // Create resource with department=engineering attribute
        CreateResourceRequest resReq = CreateResourceRequest.builder()
                .resourceType("document")
                .externalId("doc-eng-101")
                .attributes(Map.of("dept", "engineering", "sensitivity", 3))
                .build();

        MvcResult resResult = mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isCreated())
                .andReturn();

        resourceId = UUID.fromString(objectMapper.readTree(resResult.getResponse().getContentAsString()).get("id").asText());

        // Grant alice MANAGER on the resource
        CreateRelationshipRequest relReq = CreateRelationshipRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(Relation.MANAGER)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(relReq)))
                .andExpect(status().isCreated());
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
    @DisplayName("ABAC policy: Allows when subject department matches resource department")
    void authorize_matchingDepartment_allowed() throws Exception {
        // Update alice attributes to department=engineering
        updateSubjectAttributes(Map.of("dept", "engineering"));

        // Create ABAC policy requiring matching department
        createPolicy("document", Action.UPDATE, PolicyType.ABAC, "#subject['dept'] == #resource['dept']");

        AuthorizeRequest authReq = AuthorizeRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .action(Action.UPDATE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)))
                .andExpect(jsonPath("$.evaluator", is("ALL_PASSED")));
    }

    @Test
    @DisplayName("ABAC policy: Denies when subject department does not match resource department")
    void authorize_mismatchedDepartment_deniedByAbac() throws Exception {
        // Update alice attributes to department=sales
        updateSubjectAttributes(Map.of("dept", "sales"));

        // Create ABAC policy requiring matching department
        createPolicy("document", Action.UPDATE, PolicyType.ABAC, "#subject['dept'] == #resource['dept']");

        AuthorizeRequest authReq = AuthorizeRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .action(Action.UPDATE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.reason", is("ABAC_POLICY_FAILED")))
                .andExpect(jsonPath("$.evaluator", is("ABAC")));
    }

    @Test
    @DisplayName("Business Rule policy: Denies when impossible time window rule is active")
    void authorize_businessRuleFails_deniedByBusinessRule() throws Exception {
        updateSubjectAttributes(Map.of("dept", "engineering"));
        createPolicy("document", Action.UPDATE, PolicyType.ABAC, "#subject['dept'] == #resource['dept']");

        // Create Business Rule requiring hour > 24 (impossible)
        createPolicy("document", Action.UPDATE, PolicyType.BUSINESS_RULE, "#environment['currentHour'] > 24");

        AuthorizeRequest authReq = AuthorizeRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .action(Action.UPDATE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(authReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)))
                .andExpect(jsonPath("$.reason", is("BUSINESS_RULE_FAILED")))
                .andExpect(jsonPath("$.evaluator", is("BUSINESS_RULE")));
    }

    private String registerSubjectAndGetJwt(String externalId, String password) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .externalId(externalId)
                .password(password)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .header("X-API-Key", RAW_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String content = result.getResponse().getContentAsString();
        this.subjectId = UUID.fromString(objectMapper.readTree(content).get("subjectId").asText());
        return objectMapper.readTree(content).get("token").asText();
    }

    private void updateSubjectAttributes(Map<String, Object> attributes) throws Exception {
        UpdateSubjectAttributesRequest updateReq = UpdateSubjectAttributesRequest.builder()
                .attributes(attributes)
                .build();

        mockMvc.perform(put("/api/v1/subjects/{id}/attributes", subjectId)
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());
    }

    private void createPolicy(String resourceType, Action action, PolicyType type, String expression) throws Exception {
        CreatePolicyRequest request = CreatePolicyRequest.builder()
                .resourceType(resourceType)
                .action(action)
                .policyType(type)
                .expression(expression)
                .build();

        mockMvc.perform(post("/api/v1/policies")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }
}
