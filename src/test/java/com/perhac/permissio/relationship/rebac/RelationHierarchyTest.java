package com.perhac.permissio.relationship.rebac;

import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.relationship.entity.Relation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic tests for {@link RelationHierarchy} permission evaluation matrix.
 */
class RelationHierarchyTest {

    @ParameterizedTest(name = "{0} permits {1} -> {2}")
    @CsvSource({
            // OWNER: all actions allowed
            "OWNER, CREATE, true",
            "OWNER, READ, true",
            "OWNER, UPDATE, true",
            "OWNER, DELETE, true",
            "OWNER, APPROVE, true",
            "OWNER, REJECT, true",

            // MANAGER: CREATE, READ, UPDATE
            "MANAGER, CREATE, true",
            "MANAGER, READ, true",
            "MANAGER, UPDATE, true",
            "MANAGER, DELETE, false",
            "MANAGER, APPROVE, false",
            "MANAGER, REJECT, false",

            // LEAD: CREATE, READ
            "LEAD, CREATE, true",
            "LEAD, READ, true",
            "LEAD, UPDATE, false",
            "LEAD, DELETE, false",
            "LEAD, APPROVE, false",
            "LEAD, REJECT, false",

            // MEMBER: READ only
            "MEMBER, CREATE, false",
            "MEMBER, READ, true",
            "MEMBER, UPDATE, false",
            "MEMBER, DELETE, false",
            "MEMBER, APPROVE, false",
            "MEMBER, REJECT, false"
    })
    void permitsMatrix_evaluatesCorrectly(Relation relation, Action action, boolean expected) {
        assertThat(RelationHierarchy.permits(relation, action)).isEqualTo(expected);
    }

    @Test
    void getAllowedActions_owner_containsAllActions() {
        Set<Action> actions = RelationHierarchy.getAllowedActions(Relation.OWNER);
        assertThat(actions).containsExactlyInAnyOrder(
                Action.CREATE, Action.READ, Action.UPDATE, Action.DELETE, Action.APPROVE, Action.REJECT
        );
    }

    @Test
    void getAllowedActions_manager_containsCreateReadUpdate() {
        Set<Action> actions = RelationHierarchy.getAllowedActions(Relation.MANAGER);
        assertThat(actions).containsExactlyInAnyOrder(
                Action.CREATE, Action.READ, Action.UPDATE
        );
    }

    @Test
    void getAllowedActions_lead_containsCreateRead() {
        Set<Action> actions = RelationHierarchy.getAllowedActions(Relation.LEAD);
        assertThat(actions).containsExactlyInAnyOrder(
                Action.CREATE, Action.READ
        );
    }

    @Test
    void getAllowedActions_member_containsReadOnly() {
        Set<Action> actions = RelationHierarchy.getAllowedActions(Relation.MEMBER);
        assertThat(actions).containsExactlyInAnyOrder(
                Action.READ
        );
    }

    @Test
    void nullSafety_evaluatesFalse() {
        assertThat(RelationHierarchy.permits(null, Action.READ)).isFalse();
        assertThat(RelationHierarchy.permits(Relation.OWNER, null)).isFalse();
        assertThat(RelationHierarchy.permits(null, null)).isFalse();
        assertThat(RelationHierarchy.getAllowedActions(null)).isEmpty();
    }
}
