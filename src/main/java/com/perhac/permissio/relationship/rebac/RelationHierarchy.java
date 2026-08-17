package com.perhac.permissio.relationship.rebac;

import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.relationship.entity.Relation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic permission matrix mapping relationship roles to permitted actions.
 * <p>
 * Hierarchy rules:
 * <ul>
 *   <li>{@code OWNER}   ➔ {@code CREATE, READ, UPDATE, DELETE, APPROVE, REJECT}</li>
 *   <li>{@code MANAGER} ➔ {@code CREATE, READ, UPDATE}</li>
 *   <li>{@code LEAD}    ➔ {@code CREATE, READ}</li>
 *   <li>{@code MEMBER}  ➔ {@code READ}</li>
 * </ul>
 */
public final class RelationHierarchy {

    private static final Map<Relation, Set<Action>> MATRIX = new EnumMap<>(Relation.class);

    static {
        MATRIX.put(Relation.OWNER, Collections.unmodifiableSet(EnumSet.allOf(Action.class)));
        MATRIX.put(Relation.MANAGER, Collections.unmodifiableSet(EnumSet.of(
                Action.CREATE,
                Action.READ,
                Action.UPDATE
        )));
        MATRIX.put(Relation.LEAD, Collections.unmodifiableSet(EnumSet.of(
                Action.CREATE,
                Action.READ
        )));
        MATRIX.put(Relation.MEMBER, Collections.unmodifiableSet(EnumSet.of(
                Action.READ
        )));
    }

    private RelationHierarchy() {
        // Utility class
    }

    /**
     * Checks if a given relationship role permits the requested action.
     *
     * @param relation the relation role
     * @param action   the action to check
     * @return true if permitted, false otherwise
     */
    public static boolean permits(Relation relation, Action action) {
        if (relation == null || action == null) {
            return false;
        }
        Set<Action> allowed = MATRIX.get(relation);
        return allowed != null && allowed.contains(action);
    }

    /**
     * Returns the unmodifiable set of allowed actions for a relation role.
     *
     * @param relation the relation role
     * @return set of allowed actions, or empty set if relation is null
     */
    public static Set<Action> getAllowedActions(Relation relation) {
        if (relation == null) {
            return Collections.emptySet();
        }
        return MATRIX.getOrDefault(relation, Collections.emptySet());
    }
}
