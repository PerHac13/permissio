package com.perhac.permissio.subject.repository;

import com.perhac.permissio.subject.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link Subject} entities.
 * <p>
 * All queries are scoped by {@code clientId} to enforce tenant isolation.
 */
@Repository
public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findByClientIdAndExternalId(UUID clientId, String externalId);

    Optional<Subject> findByClientIdAndId(UUID clientId, UUID id);

    boolean existsByClientIdAndExternalId(UUID clientId, String externalId);
}
