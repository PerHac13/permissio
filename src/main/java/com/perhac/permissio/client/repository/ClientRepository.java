package com.perhac.permissio.client.repository;

import com.perhac.permissio.client.entity.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Client} entities.
 * <p>
 * Clients are the tenant boundary — every other entity in Permissio
 * carries a {@code client_id} foreign key back to this table.
 */
@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    Optional<Client> findByApiKeyHash(String apiKeyHash);

    Optional<Client> findByName(String name);
}
