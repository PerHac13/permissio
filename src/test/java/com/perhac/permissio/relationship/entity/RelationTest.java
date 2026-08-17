package com.perhac.permissio.relationship.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests verifying {@link Relation} rank assignments and strict ordering.
 */
class RelationTest {

    @Test
    void relationRank_strictOrdering() {
        assertThat(Relation.OWNER.rank()).isGreaterThan(Relation.MANAGER.rank());
        assertThat(Relation.MANAGER.rank()).isGreaterThan(Relation.LEAD.rank());
        assertThat(Relation.LEAD.rank()).isGreaterThan(Relation.MEMBER.rank());
    }

    @ParameterizedTest
    @CsvSource({
            "OWNER, 4",
            "MANAGER, 3",
            "LEAD, 2",
            "MEMBER, 1"
    })
    void relationDesignatedRank(Relation relation, int expectedRank) {
        assertThat(relation.rank()).isEqualTo(expectedRank);
    }
}
