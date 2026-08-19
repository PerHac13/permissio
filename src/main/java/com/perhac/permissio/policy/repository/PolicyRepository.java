package com.perhac.permissio.policy.repository;

import com.perhac.permissio.common.model.Action;
import com.perhac.permissio.policy.entity.Policy;
import com.perhac.permissio.policy.entity.PolicyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Policy} entities.
 * <p>
 * Enforces tenant isolation by always requiring {@code clientId} in all queries.
 */
@Repository
public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    /**
     * Finds a policy by tenant ID and policy ID.
     */
    Optional<Policy> findByClientIdAndId(UUID clientId, UUID id);

    /**
     * Finds all policies for a given tenant, resource type, action, and policy type.
     */
    List<Policy> findByClientIdAndResourceTypeAndActionAndPolicyType(
            UUID clientId, String resourceType, Action action, PolicyType policyType);

    /**
     * Finds all policies for a given tenant, resource type, and policy type.
     */
    List<Policy> findByClientIdAndResourceTypeAndPolicyType(
            UUID clientId, String resourceType, PolicyType policyType);

    /**
     * Lists all policies registered under a tenant.
     */
    List<Policy> findAllByClientId(UUID clientId);
}
