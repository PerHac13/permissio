package com.perhac.permissio.observability.metrics;

import com.perhac.permissio.common.model.Action;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Service for recording authorization metrics defined in TRD Section 11.7:
 * <ul>
 *   <li>{@code authz_requests_total} (counter tagged by {@code client.id}, {@code action}, {@code authz.decision})</li>
 *   <li>{@code authz_decision_duration_seconds} (timer tagged by {@code authz.evaluator})</li>
 *   <li>{@code authz_denials_total} (counter tagged by {@code authz.evaluator}, {@code authz.reason_code})</li>
 * </ul>
 */
@Component
public class AuthorizationMetrics {

    public static final String METRIC_REQUESTS_TOTAL = "authz_requests_total";
    public static final String METRIC_DURATION_SECONDS = "authz_decision_duration_seconds";
    public static final String METRIC_DENIALS_TOTAL = "authz_denials_total";

    private final MeterRegistry meterRegistry;

    public AuthorizationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record an authorization decision and its evaluation metrics.
     *
     * @param clientId    the calling tenant ID
     * @param action      the requested action
     * @param allowed     whether authorization succeeded
     * @param evaluator   the evaluator that decided (or ALL_PASSED)
     * @param reasonCode  the reason code if denied
     * @param duration    elapsed evaluation duration
     */
    public void recordDecision(UUID clientId,
                               Action action,
                               boolean allowed,
                               String evaluator,
                               String reasonCode,
                               Duration duration) {
        String clientIdStr = clientId != null ? clientId.toString() : "unknown";
        String actionStr = action != null ? action.name() : "unknown";
        String decisionStr = allowed ? "ALLOW" : "DENY";
        String evaluatorStr = evaluator != null ? evaluator : "UNKNOWN";

        // 1. Increment total requests counter
        Counter.builder(METRIC_REQUESTS_TOTAL)
                .description("Total authorization requests evaluated")
                .tag("client.id", clientIdStr)
                .tag("action", actionStr)
                .tag("authz.decision", decisionStr)
                .register(meterRegistry)
                .increment();

        // 2. Record duration timer
        Timer.builder(METRIC_DURATION_SECONDS)
                .description("Authorization decision evaluation latency in seconds")
                .tag("authz.evaluator", evaluatorStr)
                .register(meterRegistry)
                .record(duration);

        // 3. Increment denials counter if denied
        if (!allowed) {
            String reasonStr = reasonCode != null ? reasonCode : "DENIED";
            Counter.builder(METRIC_DENIALS_TOTAL)
                    .description("Total authorization requests denied")
                    .tag("authz.evaluator", evaluatorStr)
                    .tag("authz.reason_code", reasonStr)
                    .register(meterRegistry)
                    .increment();
        }
    }
}
