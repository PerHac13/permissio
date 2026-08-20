package com.perhac.permissio.relationship.repository;

import com.perhac.permissio.relationship.entity.Relation;
import com.perhac.permissio.relationship.entity.Relationship;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Relationship} entities.
 * <p>
 * Enforces tenant isolation by always requiring {@code clientId} in queries.
 */
public interface RelationshipRepository extends JpaRepository<Relationship, UUID> {

    /**
     * Finds a relationship by its primary ID and tenant ID.
     */
    Optional<Relationship> findByClientIdAndId(UUID clientId, UUID id);

    /**
     * Finds all relationships for a given subject under a tenant.
     */
    List<Relationship> findByClientIdAndSubjectId(UUID clientId, UUID subjectId);

    /**
     * Finds all relationships for a given resource under a tenant.
     */
    List<Relationship> findByClientIdAndResourceId(UUID clientId, UUID resourceId);

    /**
     * Finds all relationships matching both subject and resource under a tenant.
     */
    List<Relationship> findByClientIdAndSubjectIdAndResourceId(UUID clientId, UUID subjectId, UUID resourceId);

    /**
     * Checks if an exact relationship tuple already exists for a tenant.
     */
    boolean existsByClientIdAndSubjectIdAndResourceIdAndRelation(
            UUID clientId, UUID subjectId, UUID resourceId, Relation relation);

    /**
     * Lists all relationships registered under a tenant.
     */
    List<Relationship> findAllByClientId(UUID clientId);
}
