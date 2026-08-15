package com.perhac.permissio.subject.repository;

import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.subject.entity.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Data JPA tests for {@link SubjectRepository} using H2 in-memory database.
 * <p>
 * Verifies CRUD operations, tenant-scoped queries, and unique constraint
 * enforcement.
 */
@DataJpaTest
@ActiveProfiles("test")
class SubjectRepositoryTest {

        @Autowired
        private SubjectRepository subjectRepository;

        @Autowired
        private ClientRepository clientRepository;

        private Client clientA;
        private Client clientB;

        @BeforeEach
        void setUp() {
                subjectRepository.deleteAll();
                clientRepository.deleteAll();

                clientA = clientRepository.save(Client.builder()
                                .name("Acme HR")
                                .apiKeyHash("hash-a")
                                .createdAt(Instant.now())
                                .build());

                clientB = clientRepository.save(Client.builder()
                                .name("Beta Corp")
                                .apiKeyHash("hash-b")
                                .createdAt(Instant.now())
                                .build());
        }

        @Test
        void saveAndRetrieveSubject() {
                Subject subject = subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("user-001")
                                .passwordHash("bcrypt-hash")
                                .attributes("{\"department\":\"engineering\"}")
                                .createdAt(Instant.now())
                                .build());

                assertThat(subject.getId()).isNotNull();

                Optional<Subject> found = subjectRepository.findById(subject.getId());
                assertThat(found).isPresent();
                assertThat(found.get().getExternalId()).isEqualTo("user-001");
                assertThat(found.get().getClientId()).isEqualTo(clientA.getId());
                assertThat(found.get().getAttributes()).isEqualTo("{\"department\":\"engineering\"}");
        }

        @Test
        void findByClientIdAndExternalId_found() {
                subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("alice")
                                .passwordHash("hash")
                                .createdAt(Instant.now())
                                .build());

                Optional<Subject> result = subjectRepository.findByClientIdAndExternalId(
                                clientA.getId(), "alice");

                assertThat(result).isPresent();
                assertThat(result.get().getExternalId()).isEqualTo("alice");
        }

        @Test
        void findByClientIdAndExternalId_notFound_wrongTenant() {
                subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("alice")
                                .passwordHash("hash")
                                .createdAt(Instant.now())
                                .build());

                // Same externalId but different tenant — must not find
                Optional<Subject> result = subjectRepository.findByClientIdAndExternalId(
                                clientB.getId(), "alice");

                assertThat(result).isEmpty();
        }

        @Test
        void findByClientIdAndExternalId_notFound_wrongExternalId() {
                subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("alice")
                                .passwordHash("hash")
                                .createdAt(Instant.now())
                                .build());

                Optional<Subject> result = subjectRepository.findByClientIdAndExternalId(
                                clientA.getId(), "bob");

                assertThat(result).isEmpty();
        }

        @Test
        void findByClientIdAndId_found() {
                Subject saved = subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("alice")
                                .passwordHash("hash")
                                .createdAt(Instant.now())
                                .build());

                Optional<Subject> result = subjectRepository.findByClientIdAndId(
                                clientA.getId(), saved.getId());

                assertThat(result).isPresent();
                assertThat(result.get().getExternalId()).isEqualTo("alice");
        }

        @Test
        void findByClientIdAndId_notFound_wrongTenant() {
                Subject saved = subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("alice")
                                .passwordHash("hash")
                                .createdAt(Instant.now())
                                .build());

                // Same subject ID but different tenant — must not find
                Optional<Subject> result = subjectRepository.findByClientIdAndId(
                                clientB.getId(), saved.getId());

                assertThat(result).isEmpty();
        }

        @Test
        void existsByClientIdAndExternalId_true() {
                subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("alice")
                                .passwordHash("hash")
                                .createdAt(Instant.now())
                                .build());

                assertThat(subjectRepository.existsByClientIdAndExternalId(
                                clientA.getId(), "alice")).isTrue();
        }

        @Test
        void existsByClientIdAndExternalId_false() {
                assertThat(subjectRepository.existsByClientIdAndExternalId(
                                clientA.getId(), "nonexistent")).isFalse();
        }

        @Test
        void existsByClientIdAndExternalId_false_wrongTenant() {
                subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("alice")
                                .passwordHash("hash")
                                .createdAt(Instant.now())
                                .build());

                // Same externalId but different tenant
                assertThat(subjectRepository.existsByClientIdAndExternalId(
                                clientB.getId(), "alice")).isFalse();
        }

        @Test
        void uniqueConstraint_sameClientAndExternalId_throwsException() {
                subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("alice")
                                .passwordHash("hash1")
                                .createdAt(Instant.now())
                                .build());

                Subject duplicate = Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("alice")
                                .passwordHash("hash2")
                                .createdAt(Instant.now())
                                .build();

                assertThatThrownBy(() -> {
                        subjectRepository.saveAndFlush(duplicate);
                }).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        void sameExternalId_differentTenants_succeeds() {
                // Same externalId under different tenants should NOT violate constraint
                subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("alice")
                                .passwordHash("hash1")
                                .createdAt(Instant.now())
                                .build());

                Subject crossTenant = subjectRepository.save(Subject.builder()
                                .clientId(clientB.getId())
                                .externalId("alice")
                                .passwordHash("hash2")
                                .createdAt(Instant.now())
                                .build());

                assertThat(crossTenant.getId()).isNotNull();
                assertThat(subjectRepository.findByClientIdAndExternalId(clientA.getId(), "alice"))
                                .isPresent();
                assertThat(subjectRepository.findByClientIdAndExternalId(clientB.getId(), "alice"))
                                .isPresent();
        }

        @Test
        void defaultAttributes_isEmptyJson() {
                Subject subject = subjectRepository.save(Subject.builder()
                                .clientId(clientA.getId())
                                .externalId("default-attrs-user")
                                .passwordHash("hash")
                                .createdAt(Instant.now())
                                .build());

                Subject found = subjectRepository.findById(subject.getId()).orElseThrow();
                assertThat(found.getAttributes()).isEqualTo("{}");
        }
}
