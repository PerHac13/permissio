package com.perhac.permissio.audit.controller;

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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class AuditControllerIntegrationTest {

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

    private static final String RAW_API_KEY_A = "audit-key-a";
    private static final String RAW_API_KEY_B = "audit-key-b";

    private Client tenantA;
    private Client tenantB;

    private String jwtTokenA;
    private String jwtTokenB;

    private UUID subjectIdA;
    private UUID resourceIdA;

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

        jwtTokenA = obtainJwt(RAW_API_KEY_A, "alice", "pwd123", true);
        jwtTokenB = obtainJwt(RAW_API_KEY_B, "bob", "pwd123", false);

        resourceIdA = UUID.fromString(createResource(RAW_API_KEY_A, jwtTokenA, "document", "doc-a"));
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
    @DisplayName("Every /authorize call produces an audit log entry visible via GET /api/v1/audit-logs")
    void authorizeCall_persistsAuditLog_andIsQueryable() throws Exception {
        // Grant alice MEMBER on resource (permits READ, denies UPDATE)
        createRelationship(RAW_API_KEY_A, jwtTokenA, subjectIdA, resourceIdA, Relation.MEMBER);

        // 1. Allowed call: READ
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
                .andExpect(jsonPath("$.allowed", is(true)));

        // 2. Denied call: UPDATE
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
                .andExpect(jsonPath("$.allowed", is(false)));

        // 3. Query audit logs for Tenant A
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements", is(2)));

        // 4. Tenant B queries audit logs -> empty (tenant isolated)
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("X-API-Key", RAW_API_KEY_B)
                        .header("Authorization", "Bearer " + jwtTokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements", is(0)));
    }

    @Test
    @DisplayName("Filters audit logs by subjectId and resourceId")
    void listAuditLogs_filteredBySubjectId() throws Exception {
        createRelationship(RAW_API_KEY_A, jwtTokenA, subjectIdA, resourceIdA, Relation.OWNER);

        AuthorizeRequest req = AuthorizeRequest.builder()
                .subjectId(subjectIdA)
                .resourceId(resourceIdA)
                .action(Action.DELETE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("subjectId", subjectIdA.toString())
                        .header("X-API-Key", RAW_API_KEY_A)
                        .header("Authorization", "Bearer " + jwtTokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].action", is("DELETE")))
                .andExpect(jsonPath("$.content[0].allowed", is(true)));
    }

    private String obtainJwt(String apiKey, String externalId, String password, boolean isTenantA) throws Exception {
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
        UUID subId = UUID.fromString(objectMapper.readTree(content).get("subjectId").asText());
        if (isTenantA) {
            this.subjectIdA = subId;
        }
        return objectMapper.readTree(content).get("token").asText();
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
