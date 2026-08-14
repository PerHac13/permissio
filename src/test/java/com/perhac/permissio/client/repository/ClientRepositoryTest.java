package com.perhac.permissio.client.repository;

import com.perhac.permissio.client.entity.Client;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — ClientRepository: verifies entity persistence and query methods.
 * Uses H2 in PostgreSQL mode via the 'test' profile.
 */
@DataJpaTest
@ActiveProfiles("test")
class ClientRepositoryTest {

    @Autowired
    private ClientRepository clientRepository;

    @Test
    void saveAndLoad_roundTripsCorrectly() {
        Client client = Client.builder()
                .name("Acme HR")
                .apiKeyHash("hashed-key-123")
                .createdAt(Instant.now())
                .build();

        Client saved = clientRepository.save(client);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Acme HR");
        assertThat(saved.getApiKeyHash()).isEqualTo("hashed-key-123");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void findByApiKeyHash_returnsClient_whenHashExists() {
        Client client = Client.builder()
                .name("Acme HR")
                .apiKeyHash("known-hash")
                .createdAt(Instant.now())
                .build();
        clientRepository.save(client);

        Optional<Client> found = clientRepository.findByApiKeyHash("known-hash");

        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Acme HR");
    }

    @Test
    void findByApiKeyHash_returnsEmpty_whenHashDoesNotExist() {
        Optional<Client> found = clientRepository.findByApiKeyHash("nonexistent-hash");
        assertThat(found).isEmpty();
    }

    @Test
    void findByName_returnsClient_whenNameExists() {
        Client client = Client.builder()
                .name("Project Tool")
                .apiKeyHash("some-hash")
                .createdAt(Instant.now())
                .build();
        clientRepository.save(client);

        Optional<Client> found = clientRepository.findByName("Project Tool");

        assertThat(found).isPresent();
        assertThat(found.get().getApiKeyHash()).isEqualTo("some-hash");
    }

    @Test
    void findByName_returnsEmpty_whenNameDoesNotExist() {
        Optional<Client> found = clientRepository.findByName("Ghost App");
        assertThat(found).isEmpty();
    }

    @Test
    void save_generatesUuidId() {
        Client client = Client.builder()
                .name("Test Client")
                .apiKeyHash("test-hash")
                .createdAt(Instant.now())
                .build();

        Client saved = clientRepository.save(client);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getId()).isInstanceOf(UUID.class);
    }
}
