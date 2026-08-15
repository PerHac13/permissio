package com.perhac.permissio.subject.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * Represents a user/actor (Subject) registered under a specific tenant (Client).
 * <p>
 * Each Subject is uniquely identified by the combination of
 * {@code (clientId, externalId)}. The {@code attributes} field stores
 * a JSON map for future ABAC policy evaluation.
 *
 * @see com.perhac.permissio.client.entity.Client
 */
@Entity
@Table(name = "subjects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    @Builder.Default
    private String attributes = "{}";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
