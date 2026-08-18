package com.perhac.permissio.authorization.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionTest {

    @Test
    void allow_createsAllowedDecisionWithoutReason() {
        Decision decision = Decision.allow("REBAC");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isNull();
        assertThat(decision.evaluator()).isEqualTo("REBAC");
    }

    @Test
    void deny_createsDeniedDecisionWithReasonAndEvaluator() {
        Decision decision = Decision.deny("RELATION_INSUFFICIENT", "REBAC");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("RELATION_INSUFFICIENT");
        assertThat(decision.evaluator()).isEqualTo("REBAC");
    }

    @Test
    void recordConstructor_setsAllFields() {
        Decision decision = new Decision(false, "NO_RELATIONSHIP", "REBAC");

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("NO_RELATIONSHIP");
        assertThat(decision.evaluator()).isEqualTo("REBAC");
    }
}
