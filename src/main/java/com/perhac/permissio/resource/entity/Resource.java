package com.perhac.permissio.resource.entity;

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
 * Represents a generic target resource registered under a specific tenant (Client).
 * <p>
 * Each Resource is uniquely identified within a tenant by the compound key
 * {@code (clientId, resourceType, externalId)}. The {@code attributes} field stores
 * a JSON map used for ABAC/ReBAC policy evaluations.
 *
 * @see com.perhac.permissio.client.entity.Client
 */
@Entity
@Table(name = "resources")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "resource_type", nullable = false)
    private String resourceType;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(nullable = false)
    @Builder.Default
    private String attributes = "{}";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
