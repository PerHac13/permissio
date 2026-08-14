package com.perhac.permissio.client.entity;

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
 * Represents a registered client application (tenant) in Permissio.
 * <p>
 * Every consuming application is a tenant. All downstream entities
 * (Subject, Resource, Relationship, Policy, AuditLog) are scoped
 * by the client's {@code id}.
 *
 * @see com.perhac.permissio.security.TenantContext
 */
@Entity
@Table(name = "clients")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
