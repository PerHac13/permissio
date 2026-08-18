package com.perhac.permissio.authorization.evaluator;

import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.relationship.entity.Relation;
import com.perhac.permissio.relationship.entity.Relationship;
import com.perhac.permissio.resource.entity.Resource;
import com.perhac.permissio.subject.entity.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RebacEvaluatorTest {

    private RebacEvaluator rebacEvaluator;

    private UUID clientId;
    private Subject subject;
    private Resource targetResource;
    private Resource otherResource;

    @BeforeEach
    void setUp() {
        rebacEvaluator = new RebacEvaluator();
        clientId = UUID.randomUUID();

        subject = Subject.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .externalId("user-1")
                .passwordHash("hashed")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();

        targetResource = Resource.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("document")
                .externalId("doc-100")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();

        otherResource = Resource.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .resourceType("document")
                .externalId("doc-200")
                .attributes("{}")
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Returns NO_RELATIONSHIP deny when subject has no relationships")
    void evaluate_emptyRelationships_returnsNoRelationshipDeny() {
        AuthorizationContext ctx = new AuthorizationContext(
                clientId, subject, targetResource, Action.READ, List.of()
        );

        Decision decision = rebacEvaluator.evaluate(ctx);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("NO_RELATIONSHIP");
        assertThat(decision.evaluator()).isEqualTo("REBAC");
    }

    @Test
    @DisplayName("Returns NO_RELATIONSHIP deny when relationships exist only for other resources")
    void evaluate_relationshipOnOtherResource_returnsNoRelationshipDeny() {
        Relationship rel = Relationship.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .subjectId(subject.getId())
                .resourceId(otherResource.getId())
                .relation(Relation.OWNER)
                .createdAt(Instant.now())
                .build();

        AuthorizationContext ctx = new AuthorizationContext(
                clientId, subject, targetResource, Action.READ, List.of(rel)
        );

        Decision decision = rebacEvaluator.evaluate(ctx);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo("NO_RELATIONSHIP");
        assertThat(decision.evaluator()).isEqualTo("REBAC");
    }

    @ParameterizedTest(name = "Relation {0} attempting {1} should be allowed={2}")
    @CsvSource({
            "MEMBER, READ, true",
            "MEMBER, UPDATE, false",
            "LEAD, READ, true",
            "LEAD, CREATE, true",
            "LEAD, UPDATE, false",
            "MANAGER, UPDATE, true",
            "MANAGER, DELETE, false",
            "OWNER, DELETE, true",
            "OWNER, APPROVE, true",
            "OWNER, REJECT, true"
    })
    void evaluate_singleRelationship_evaluatesCorrectly(Relation relation, Action action, boolean expectedAllowed) {
        Relationship rel = Relationship.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .subjectId(subject.getId())
                .resourceId(targetResource.getId())
                .relation(relation)
                .createdAt(Instant.now())
                .build();

        AuthorizationContext ctx = new AuthorizationContext(
                clientId, subject, targetResource, action, List.of(rel)
        );

        Decision decision = rebacEvaluator.evaluate(ctx);

        assertThat(decision.allowed()).isEqualTo(expectedAllowed);
        if (expectedAllowed) {
            assertThat(decision.reason()).isNull();
        } else {
            assertThat(decision.reason()).isEqualTo("RELATION_INSUFFICIENT");
        }
        assertThat(decision.evaluator()).isEqualTo("REBAC");
    }

    @Test
    @DisplayName("Resolves highest rank relation when subject holds multiple relations on target resource")
    void evaluate_multipleRelationships_usesHighestRankRelation() {
        Relationship memberRel = Relationship.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .subjectId(subject.getId())
                .resourceId(targetResource.getId())
                .relation(Relation.MEMBER)
                .createdAt(Instant.now())
                .build();

        Relationship managerRel = Relationship.builder()
                .id(UUID.randomUUID())
                .clientId(clientId)
                .subjectId(subject.getId())
                .resourceId(targetResource.getId())
                .relation(Relation.MANAGER)
                .createdAt(Instant.now())
                .build();

        // MEMBER alone cannot UPDATE, but MANAGER can UPDATE
        AuthorizationContext ctx = new AuthorizationContext(
                clientId, subject, targetResource, Action.UPDATE, List.of(memberRel, managerRel)
        );

        Decision decision = rebacEvaluator.evaluate(ctx);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isNull();
        assertThat(decision.evaluator()).isEqualTo("REBAC");
    }

    @Test
    void metadata_returnsExpectedNameAndOrder() {
        assertThat(rebacEvaluator.name()).isEqualTo("REBAC");
        assertThat(rebacEvaluator.getOrder()).isEqualTo(1);
    }
}
