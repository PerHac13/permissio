package com.perhac.permissio.authorization.evaluator;

import com.perhac.permissio.authorization.model.AuthorizationContext;
import com.perhac.permissio.authorization.model.Decision;
import com.perhac.permissio.relationship.entity.Relation;
import com.perhac.permissio.relationship.entity.Relationship;
import com.perhac.permissio.relationship.rebac.RelationHierarchy;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Evaluates authorization requests based on Relationship-Based Access Control (ReBAC).
 * <p>
 * Inspects relationships between the subject and the target resource to determine the highest
 * rank relation role, then checks whether that relation permits the requested action via
 * {@link RelationHierarchy}.
 */
@Component
@Order(1)
public class RebacEvaluator implements PolicyEvaluator {

    public static final String EVALUATOR_NAME = "REBAC";
    public static final String REASON_NO_RELATIONSHIP = "NO_RELATIONSHIP";
    public static final String REASON_RELATION_INSUFFICIENT = "RELATION_INSUFFICIENT";

    @Override
    public Decision evaluate(AuthorizationContext context) {
        if (context == null || context.resource() == null) {
            return Decision.deny(REASON_NO_RELATIONSHIP, EVALUATOR_NAME);
        }

        UUID targetResourceId = context.resource().getId();
        List<Relationship> matchingRelationships = context.relationships().stream()
                .filter(rel -> rel.getResourceId().equals(targetResourceId))
                .toList();

        if (matchingRelationships.isEmpty()) {
            return Decision.deny(REASON_NO_RELATIONSHIP, EVALUATOR_NAME);
        }

        Optional<Relation> highestRelation = matchingRelationships.stream()
                .map(Relationship::getRelation)
                .max(Comparator.comparingInt(Relation::rank));

        if (highestRelation.isEmpty()) {
            return Decision.deny(REASON_NO_RELATIONSHIP, EVALUATOR_NAME);
        }

        boolean permitted = RelationHierarchy.permits(highestRelation.get(), context.action());
        if (permitted) {
            return Decision.allow(EVALUATOR_NAME);
        }

        return Decision.deny(REASON_RELATION_INSUFFICIENT, EVALUATOR_NAME);
    }

    @Override
    public String name() {
        return EVALUATOR_NAME;
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
