package com.perhac.permissio.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ticket 12.1 — Smoke test verifying that springdoc-openapi is correctly wired.
 * <p>
 * Asserts:
 * <ul>
 *   <li>{@code GET /v3/api-docs} returns valid OpenAPI JSON</li>
 *   <li>The spec version starts with {@code 3.}</li>
 *   <li>The {@code /api/v1/authorize} path is documented</li>
 *   <li>The Swagger UI page is accessible</li>
 * </ul>
 */
@SpringBootTest
@ActiveProfiles("test")
class OpenApiSmokeTest {

    @Autowired
    private WebApplicationContext wac;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    }

    @Test
    void apiDocsEndpoint_returnsValidOpenApiSpec() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi", startsWith("3.")))
                .andExpect(jsonPath("$.info.title").exists())
                .andExpect(jsonPath("$.paths").exists());
    }

    @Test
    void apiDocsEndpoint_containsAuthorizeEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/authorize']").exists());
    }

    @Test
    void apiDocsEndpoint_containsSubjectsEndpoint() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/subjects']").exists());
    }

    @Test
    void swaggerUiPage_isAccessible() throws Exception {
        // Swagger UI redirects to /swagger-ui/index.html
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
