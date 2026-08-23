package com.perhac.permissio.security;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.service.ClientService;
import com.perhac.permissio.common.exception.UnauthorizedException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Ticket 10.4 — Verifies that the raw API key value never appears in log output,
 * even at DEBUG level, during filter processing.
 * <p>
 * Uses a Logback {@link ListAppender} to capture all log events emitted by
 * {@link ApiKeyAuthenticationFilter} and asserts none of them contain the raw key string.
 */
@ExtendWith(MockitoExtension.class)
class ApiKeyLogSanitizationTest {

    private static final String RAW_API_KEY = "super-secret-api-key-abc123";

    @Mock
    private ClientService clientService;

    @Mock
    private FilterChain filterChain;

    private ApiKeyAuthenticationFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger filterLogger;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(clientService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();

        // Attach a ListAppender to capture log events from the filter's logger
        filterLogger = (Logger) LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        filterLogger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        filterLogger.detachAppender(logAppender);
        TenantContext.clear();
    }

    @Test
    void validApiKey_neverLogsRawKeyValue() throws Exception {
        Client client = Client.builder()
                .id(UUID.randomUUID())
                .name("Test Client")
                .apiKeyHash("hashed-value")
                .createdAt(Instant.now())
                .build();

        when(clientService.resolveByApiKey(RAW_API_KEY)).thenReturn(client);
        request.addHeader("X-API-Key", RAW_API_KEY);

        filter.doFilterInternal(request, response, filterChain);

        assertNoLogContainsRawKey();
    }

    @Test
    void invalidApiKey_neverLogsRawKeyValue() throws Exception {
        when(clientService.resolveByApiKey(RAW_API_KEY))
                .thenThrow(new UnauthorizedException("Invalid API key"));
        request.addHeader("X-API-Key", RAW_API_KEY);

        filter.doFilterInternal(request, response, filterChain);

        assertNoLogContainsRawKey();
    }

    @Test
    void missingApiKey_neverLogsRawKeyValue() throws Exception {
        // No X-API-Key header
        filter.doFilterInternal(request, response, filterChain);

        assertNoLogContainsRawKey();
    }

    private void assertNoLogContainsRawKey() {
        for (ILoggingEvent event : logAppender.list) {
            String formattedMessage = event.getFormattedMessage();
            assertThat(formattedMessage)
                    .as("Log at level %s should not contain raw API key: %s",
                            event.getLevel(), formattedMessage)
                    .doesNotContain(RAW_API_KEY);
        }
    }
}
