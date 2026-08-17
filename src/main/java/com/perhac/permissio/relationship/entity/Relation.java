package com.perhac.permissio.relationship.entity;

/**
 * Relationship roles with explicit rank ordering for ReBAC evaluation.
 * Higher rank indicates broader scope and permission dominance.
 */
public enum Relation {
    OWNER(4),
    MANAGER(3),
    LEAD(2),
    MEMBER(1);

    private final int rank;

    Relation(int rank) {
        this.rank = rank;
    }

    public int rank() {
        return rank;
    }
}
