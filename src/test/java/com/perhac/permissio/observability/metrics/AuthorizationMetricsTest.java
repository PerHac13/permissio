package com.perhac.permissio.observability.metrics;

import com.perhac.permissio.common.model.Action;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorizationMetricsTest {

    private MeterRegistry meterRegistry;
    private AuthorizationMetrics metrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new AuthorizationMetrics(meterRegistry);
    }

    @Test
    @DisplayName("Records allowed decision: increments authz_requests_total and records timer")
    void recordDecision_allowed_recordsRequestAndDuration() {
        UUID clientId = UUID.randomUUID();
        metrics.recordDecision(clientId, Action.READ, true, "REBAC", "ALLOWED", Duration.ofMillis(12));

        Counter requestCounter = meterRegistry.find("authz_requests_total")
                .tag("client.id", clientId.toString())
                .tag("action", "READ")
                .tag("authz.decision", "ALLOW")
                .counter();

        assertThat(requestCounter).isNotNull();
        assertThat(requestCounter.count()).isEqualTo(1.0);

        Timer durationTimer = meterRegistry.find("authz_decision_duration_seconds")
                .tag("authz.evaluator", "REBAC")
                .timer();

        assertThat(durationTimer).isNotNull();
        assertThat(durationTimer.count()).isEqualTo(1);
        assertThat(durationTimer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThanOrEqualTo(12);

        // Denials counter must not be incremented
        Counter denialCounter = meterRegistry.find("authz_denials_total").counter();
        assertThat(denialCounter).isNull();
    }

    @Test
    @DisplayName("Records denied decision: increments authz_requests_total, authz_denials_total, and records timer")
    void recordDecision_denied_recordsRequestDenialAndDuration() {
        UUID clientId = UUID.randomUUID();
        metrics.recordDecision(clientId, Action.DELETE, false, "REBAC", "NO_RELATIONSHIP", Duration.ofMillis(5));

        Counter requestCounter = meterRegistry.find("authz_requests_total")
                .tag("client.id", clientId.toString())
                .tag("action", "DELETE")
                .tag("authz.decision", "DENY")
                .counter();

        assertThat(requestCounter).isNotNull();
        assertThat(requestCounter.count()).isEqualTo(1.0);

        Counter denialCounter = meterRegistry.find("authz_denials_total")
                .tag("authz.evaluator", "REBAC")
                .tag("authz.reason_code", "NO_RELATIONSHIP")
                .counter();

        assertThat(denialCounter).isNotNull();
        assertThat(denialCounter.count()).isEqualTo(1.0);

        Timer durationTimer = meterRegistry.find("authz_decision_duration_seconds")
                .tag("authz.evaluator", "REBAC")
                .timer();

        assertThat(durationTimer).isNotNull();
        assertThat(durationTimer.count()).isEqualTo(1);
    }
}
