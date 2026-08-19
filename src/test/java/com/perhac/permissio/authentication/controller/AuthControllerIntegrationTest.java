package com.perhac.permissio.authentication.controller;

import tools.jackson.databind.ObjectMapper;
import com.perhac.permissio.authentication.dto.LoginRequest;
import com.perhac.permissio.authentication.dto.RegisterRequest;
import com.perhac.permissio.audit.repository.AuditLogRepository;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.policy.repository.PolicyRepository;
import com.perhac.permissio.relationship.repository.RelationshipRepository;
import com.perhac.permissio.resource.repository.ResourceRepository;
import com.perhac.permissio.security.ApiKeyHasher;
import com.perhac.permissio.subject.repository.SubjectRepository;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full HTTP integration tests for the authentication flow.
 * <p>
 * Uses a real Spring context with H2 database to test complete
 * register → login → JWT roundtrips through all layers.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

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

        private static final String RAW_API_KEY = "integration-test-api-key";
        private Client testClient;

        @BeforeEach
        void setUp() {
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

                testClient = clientRepository.save(Client.builder()
                                .name("Integration Test Tenant")
                                .apiKeyHash(apiKeyHasher.hash(RAW_API_KEY))
                                .createdAt(Instant.now())
                                .build());
        }

        // =========================================================================
        // Registration Tests
        // =========================================================================

        @Test
        void register_returns201WithJwt() throws Exception {
                RegisterRequest request = RegisterRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.token", notNullValue()))
                                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                                .andExpect(jsonPath("$.expiresIn", is(900000)))
                                .andExpect(jsonPath("$.subjectId", notNullValue()))
                                .andExpect(jsonPath("$.externalId", is("alice")));
        }

        @Test
        void register_withAttributes_returns201() throws Exception {
                RegisterRequest request = RegisterRequest.builder()
                                .externalId("bob")
                                .password("secret123")
                                .attributes(Map.of("department", "engineering"))
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.externalId", is("bob")));
        }

        @Test
        void register_duplicateExternalId_returns409() throws Exception {
                RegisterRequest request = RegisterRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                // First registration succeeds
                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                // Duplicate registration fails
                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.code", is("CONFLICT")))
                                .andExpect(jsonPath("$.message")
                                                .value(org.hamcrest.Matchers.containsString("already exists")));
        }

        @Test
        void register_missingExternalId_returns400() throws Exception {
                RegisterRequest request = RegisterRequest.builder()
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void register_passwordTooShort_returns400() throws Exception {
                RegisterRequest request = RegisterRequest.builder()
                                .externalId("alice")
                                .password("12345") // < 6 chars
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void register_missingApiKey_returns401() throws Exception {
                RegisterRequest request = RegisterRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void register_invalidApiKey_returns401() throws Exception {
                RegisterRequest request = RegisterRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", "invalid-api-key")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized());
        }

        // =========================================================================
        // Login Tests
        // =========================================================================

        @Test
        void login_afterRegister_returns200WithJwt() throws Exception {
                // Register first
                RegisterRequest registerRequest = RegisterRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerRequest)))
                                .andExpect(status().isCreated());

                // Then login
                LoginRequest loginRequest = LoginRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.token", notNullValue()))
                                .andExpect(jsonPath("$.tokenType", is("Bearer")))
                                .andExpect(jsonPath("$.expiresIn", is(900000)))
                                .andExpect(jsonPath("$.subjectId", notNullValue()))
                                .andExpect(jsonPath("$.externalId", is("alice")));
        }

        @Test
        void login_wrongPassword_returns401() throws Exception {
                // Register first
                RegisterRequest registerRequest = RegisterRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerRequest)))
                                .andExpect(status().isCreated());

                // Login with wrong password
                LoginRequest loginRequest = LoginRequest.builder()
                                .externalId("alice")
                                .password("wrong-password")
                                .build();

                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
        }

        @Test
        void login_unknownUser_returns401() throws Exception {
                LoginRequest loginRequest = LoginRequest.builder()
                                .externalId("nonexistent")
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isUnauthorized())
                                .andExpect(jsonPath("$.code", is("UNAUTHORIZED")));
        }

        @Test
        void login_missingApiKey_returns401() throws Exception {
                LoginRequest loginRequest = LoginRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isUnauthorized());
        }

        // =========================================================================
        // Cross-Tenant Isolation Tests
        // =========================================================================

        @Test
        void register_sameName_differentTenants_bothSucceed() throws Exception {
                // Create a second tenant
                String secondApiKey = "second-tenant-api-key";
                clientRepository.save(Client.builder()
                                .name("Second Tenant")
                                .apiKeyHash(apiKeyHasher.hash(secondApiKey))
                                .createdAt(Instant.now())
                                .build());

                RegisterRequest request = RegisterRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                // Register "alice" under first tenant
                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                // Register "alice" under second tenant — should succeed
                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", secondApiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());
        }

        @Test
        void login_wrongTenant_returns401() throws Exception {
                // Create a second tenant
                String secondApiKey = "second-tenant-api-key-2";
                clientRepository.save(Client.builder()
                                .name("Second Tenant 2")
                                .apiKeyHash(apiKeyHasher.hash(secondApiKey))
                                .createdAt(Instant.now())
                                .build());

                // Register "alice" under first tenant
                RegisterRequest request = RegisterRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated());

                // Try to login as "alice" under second tenant — should fail
                LoginRequest loginRequest = LoginRequest.builder()
                                .externalId("alice")
                                .password("secret123")
                                .build();

                mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-API-Key", secondApiKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isUnauthorized());
        }

        // =========================================================================
        // Full Roundtrip: Register → Login → Verify JWT Structure
        // =========================================================================

        @Test
        void fullRoundtrip_registerAndLogin_tokensAreValid() throws Exception {
                RegisterRequest registerRequest = RegisterRequest.builder()
                                .externalId("roundtrip-user")
                                .password("secret123")
                                .build();

                // Register
                MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(registerRequest)))
                                .andExpect(status().isCreated())
                                .andReturn();

                String registerToken = objectMapper.readTree(
                                registerResult.getResponse().getContentAsString()).get("token").asText();
                assertThat(registerToken).isNotBlank();
                assertThat(registerToken.split("\\.")).hasSize(3); // JWT format

                // Login
                LoginRequest loginRequest = LoginRequest.builder()
                                .externalId("roundtrip-user")
                                .password("secret123")
                                .build();

                MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                                .header("X-API-Key", RAW_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(loginRequest)))
                                .andExpect(status().isOk())
                                .andReturn();

                String loginToken = objectMapper.readTree(
                                loginResult.getResponse().getContentAsString()).get("token").asText();
                assertThat(loginToken).isNotBlank();
                assertThat(loginToken.split("\\.")).hasSize(3); // JWT format

                // Both should return the same subjectId
                String registerSubjectId = objectMapper.readTree(
                                registerResult.getResponse().getContentAsString()).get("subjectId").asText();
                String loginSubjectId = objectMapper.readTree(
                                loginResult.getResponse().getContentAsString()).get("subjectId").asText();
                assertThat(registerSubjectId).isEqualTo(loginSubjectId);
        }
}
