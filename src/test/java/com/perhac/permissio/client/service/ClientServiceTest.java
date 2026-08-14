package com.perhac.permissio.client.service;

import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.common.exception.UnauthorizedException;
import com.perhac.permissio.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TDD — ClientService: resolves clients from API keys and registers new clients.
 */
@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    private ApiKeyHasher apiKeyHasher;
    private ClientService clientService;

    @BeforeEach
    void setUp() {
        apiKeyHasher = new ApiKeyHasher("test-salt");
        clientService = new ClientService(clientRepository, apiKeyHasher);
    }

    // --- resolveByApiKey ---

    @Test
    void resolveByApiKey_throwsUnauthorized_whenKeyNotFound() {
        when(clientRepository.findByApiKeyHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> clientService.resolveByApiKey("unknown-key"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("Invalid API key");
    }

    @Test
    void resolveByApiKey_returnsClient_whenKeyIsValid() {
        String rawApiKey = "valid-api-key";
        String hashedKey = apiKeyHasher.hash(rawApiKey);

        Client expectedClient = Client.builder()
                .id(UUID.randomUUID())
                .name("Acme HR")
                .apiKeyHash(hashedKey)
                .createdAt(Instant.now())
                .build();

        when(clientRepository.findByApiKeyHash(hashedKey)).thenReturn(Optional.of(expectedClient));

        Client result = clientService.resolveByApiKey(rawApiKey);

        assertThat(result).isEqualTo(expectedClient);
        assertThat(result.getName()).isEqualTo("Acme HR");
    }

    // --- registerClient ---

    @Test
    void registerClient_storesHashedKey_notPlaintext() {
        String rawApiKey = "my-secret-key";

        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
            Client c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        clientService.registerClient("Test App", rawApiKey);

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());

        Client saved = captor.getValue();
        assertThat(saved.getApiKeyHash()).isNotEqualTo(rawApiKey);
        assertThat(saved.getApiKeyHash()).isEqualTo(apiKeyHasher.hash(rawApiKey));
        assertThat(saved.getName()).isEqualTo("Test App");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void registerClient_returnsClientWithId() {
        UUID expectedId = UUID.randomUUID();
        when(clientRepository.save(any(Client.class))).thenAnswer(invocation -> {
            Client c = invocation.getArgument(0);
            c.setId(expectedId);
            return c;
        });

        Client result = clientService.registerClient("New App", "new-api-key");

        assertThat(result.getId()).isEqualTo(expectedId);
        assertThat(result.getName()).isEqualTo("New App");
    }
}
