package com.perhac.permissio.resource.repository;

import com.perhac.permissio.resource.entity.Resource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Resource} entities.
 * <p>
 * All queries are scoped by {@code clientId} to enforce strict tenant isolation.
 */
@Repository
public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    Optional<Resource> findByClientIdAndId(UUID clientId, UUID id);

    Optional<Resource> findByClientIdAndResourceTypeAndExternalId(UUID clientId, String resourceType, String externalId);

    boolean existsByClientIdAndResourceTypeAndExternalId(UUID clientId, String resourceType, String externalId);

    List<Resource> findAllByClientId(UUID clientId);

    List<Resource> findAllByClientIdAndResourceType(UUID clientId, String resourceType);
}
