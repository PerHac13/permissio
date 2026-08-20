package com.perhac.permissio.observability;

import com.perhac.permissio.audit.entity.AuditLog;
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
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
class ObservabilityIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MeterRegistry meterRegistry;

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

    private static final String RAW_API_KEY = "otel-test-key";
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

        clientRepository.save(Client.builder()
                .name("Observability Tenant")
                .apiKeyHash(apiKeyHasher.hash(RAW_API_KEY))
                .createdAt(Instant.now())
                .build());

        jwtToken = registerSubjectAndGetJwt("observability-user", "password123");
        resourceId = createResource("document", "doc-obs-1");
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
    @DisplayName("End-to-end /authorize call returns X-Trace-Id header, records trace in audit log, and increments metrics")
    void authorize_observabilityPipeline_recordsTraceMetricsAndAudit() throws Exception {
        // Grant MEMBER relationship (allows READ, denies DELETE)
        createRelationship(subjectId, resourceId, Relation.MEMBER);

        AuthorizeRequest readReq = AuthorizeRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .action(Action.READ)
                .build();

        // 1. Execute allowed /authorize call
        MvcResult result = mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(readReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(true)))
                .andReturn();

        // Verify response contains X-Trace-Id header
        String traceHeader = result.getResponse().getHeader("X-Trace-Id");
        assertThat(traceHeader).isNotBlank();

        // Verify audit log has the matching trace ID
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertThat(auditLogs).hasSize(1);
        assertThat(auditLogs.get(0).getTraceId()).isEqualTo(traceHeader);
        assertThat(auditLogs.get(0).isAllowed()).isTrue();

        // Verify metrics
        Counter requestCounter = meterRegistry.find("authz_requests_total")
                .tag("authz.decision", "ALLOW")
                .tag("action", "READ")
                .counter();
        assertThat(requestCounter).isNotNull();
        assertThat(requestCounter.count()).isGreaterThanOrEqualTo(1.0);

        Timer durationTimer = meterRegistry.find("authz_decision_duration_seconds").timer();
        assertThat(durationTimer).isNotNull();
        assertThat(durationTimer.count()).isGreaterThanOrEqualTo(1);

        // 2. Execute denied /authorize call
        AuthorizeRequest deleteReq = AuthorizeRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .action(Action.DELETE)
                .build();

        mockMvc.perform(post("/api/v1/authorize")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed", is(false)));

        // Verify denial metric counter
        Counter denialCounter = meterRegistry.find("authz_denials_total").counter();
        assertThat(denialCounter).isNotNull();
        assertThat(denialCounter.count()).isGreaterThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("Actuator /actuator/prometheus and /actuator/metrics endpoints are exposed and functional")
    void actuatorEndpoints_areAccessible() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
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
        this.subjectId = UUID.fromString(objectMapper.readTree(content).get("subjectId").asString());
        return objectMapper.readTree(content).get("token").asString();
    }

    private UUID createResource(String resourceType, String externalId) throws Exception {
        CreateResourceRequest req = CreateResourceRequest.builder()
                .resourceType(resourceType)
                .externalId(externalId)
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/resources")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        return UUID.fromString(objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString());
    }

    private void createRelationship(UUID subjectId, UUID resourceId, Relation relation) throws Exception {
        CreateRelationshipRequest req = CreateRelationshipRequest.builder()
                .subjectId(subjectId)
                .resourceId(resourceId)
                .relation(relation)
                .build();

        mockMvc.perform(post("/api/v1/relationships")
                        .header("X-API-Key", RAW_API_KEY)
                        .header("Authorization", "Bearer " + jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }
}
