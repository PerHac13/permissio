package com.perhac.permissio.relationship.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a tenant-scoped relationship tuple (Subject ➔ Relation ➔ Resource).
 * <p>
 * Each relationship assigns a specific role ({@link Relation}) to a Subject for a Resource
 * within a tenant (Client). Uniqueness is enforced on the tuple
 * {@code (clientId, subjectId, resourceId, relation)}.
 *
 * @see Relation
 */
@Entity
@Table(name = "relationships")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Relationship {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", nullable = false, length = 50)
    private Relation relation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
