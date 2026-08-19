package com.perhac.permissio.policy.engine;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyEvaluationEngineTest {

    private PolicyEvaluationEngine engine;

    @BeforeEach
    void setUp() {
        engine = new PolicyEvaluationEngine(new ObjectMapper());
    }

    @Test
    @DisplayName("Successfully evaluates matching department attribute")
    void evaluate_matchingAttributes_returnsTrue() {
        String subjectJson = "{\"department\":\"engineering\",\"clearance\":3}";
        String resourceJson = "{\"department\":\"engineering\",\"sensitivity\":2}";

        boolean result = engine.evaluate(
                "#subject['department'] == #resource['department']",
                subjectJson,
                resourceJson,
                "READ",
                Map.of()
        );

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("Returns false when attributes do not match")
    void evaluate_mismatchedAttributes_returnsFalse() {
        String subjectJson = "{\"department\":\"sales\"}";
        String resourceJson = "{\"department\":\"engineering\"}";

        boolean result = engine.evaluate(
                "#subject['department'] == #resource['department']",
                subjectJson,
                resourceJson,
                "READ",
                Map.of()
        );

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Evaluates numeric comparison on attributes")
    void evaluate_numericComparison_evaluatesCorrectly() {
        String subjectJson = "{\"clearance\":5}";
        String resourceJson = "{\"requiredClearance\":3}";

        boolean allowed = engine.evaluate(
                "#subject['clearance'] >= #resource['requiredClearance']",
                subjectJson,
                resourceJson,
                "READ",
                Map.of()
        );
        assertThat(allowed).isTrue();

        boolean denied = engine.evaluate(
                "#subject['clearance'] < #resource['requiredClearance']",
                subjectJson,
                resourceJson,
                "READ",
                Map.of()
        );
        assertThat(denied).isFalse();
    }

    @Test
    @DisplayName("Evaluates environment variables (e.g. current hour)")
    void evaluate_environmentVariables_evaluatesCorrectly() {
        Map<String, Object> environment = Map.of("currentHour", 14);

        boolean allowed = engine.evaluate(
                "#environment['currentHour'] >= 9 and #environment['currentHour'] < 17",
                "{}",
                "{}",
                "UPDATE",
                environment
        );

        assertThat(allowed).isTrue();
    }

    @Test
    @DisplayName("Sandboxing: Blocks arbitrary Java class access T(java.lang.Runtime)")
    void evaluate_maliciousExpression_blockedBySandbox() {
        // Attempt arbitrary command execution / class reference
        String maliciousExpr = "T(java.lang.Runtime).getRuntime().totalMemory() > 0";

        boolean result = engine.evaluate(
                maliciousExpr,
                "{}",
                "{}",
                "READ",
                Map.of()
        );

        // Sandboxed SimpleEvaluationContext does not permit T(...) type references and fails safely
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Handles invalid JSON and missing fields gracefully without crashing")
    void evaluate_invalidJsonOrMissingField_returnsFalse() {
        boolean result = engine.evaluate(
                "#subject['nonexistent'] == 'admin'",
                "not-valid-json",
                "{}",
                "READ",
                Map.of()
        );

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Handles null or blank expression by returning true (no-op)")
    void evaluate_blankExpression_returnsTrue() {
        assertThat(engine.evaluate(null, "{}", "{}", "READ", Map.of())).isTrue();
        assertThat(engine.evaluate("   ", "{}", "{}", "READ", Map.of())).isTrue();
    }
}
