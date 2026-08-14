package com.perhac.permissio.client.service;

import com.perhac.permissio.client.entity.Client;
import com.perhac.permissio.client.repository.ClientRepository;
import com.perhac.permissio.common.exception.UnauthorizedException;
import com.perhac.permissio.security.ApiKeyHasher;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Service for client (tenant) management.
 * <p>
 * Resolves a client from an API key (used by {@link com.perhac.permissio.security.ApiKeyAuthenticationFilter})
 * and registers new client applications.
 */
@Service
public class ClientService {

    private final ClientRepository clientRepository;
    private final ApiKeyHasher apiKeyHasher;

    public ClientService(ClientRepository clientRepository, ApiKeyHasher apiKeyHasher) {
        this.clientRepository = clientRepository;
        this.apiKeyHasher = apiKeyHasher;
    }

    /**
     * Resolves a client from a raw (unhashed) API key.
     *
     * @param rawApiKey the plaintext API key from the {@code X-API-Key} header
     * @return the resolved {@link Client}
     * @throws UnauthorizedException if no client matches the hashed key
     */
    public Client resolveByApiKey(String rawApiKey) {
        String hashedKey = apiKeyHasher.hash(rawApiKey);
        return clientRepository.findByApiKeyHash(hashedKey)
                .orElseThrow(() -> new UnauthorizedException("Invalid API key"));
    }

    /**
     * Registers a new client application with Permissio.
     * The API key is hashed before storage — plaintext is never persisted.
     *
     * @param name      the client application name
     * @param rawApiKey the plaintext API key to associate with this client
     * @return the persisted {@link Client} with generated ID
     */
    public Client registerClient(String name, String rawApiKey) {
        Client client = Client.builder()
                .name(name)
                .apiKeyHash(apiKeyHasher.hash(rawApiKey))
                .createdAt(Instant.now())
                .build();
        return clientRepository.save(client);
    }
}
