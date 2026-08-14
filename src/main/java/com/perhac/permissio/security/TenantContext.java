package com.perhac.permissio.security;

import java.util.UUID;

/**
 * ThreadLocal-backed holder for the current tenant's client ID.
 * <p>
 * Set once per request in {@link ApiKeyAuthenticationFilter},
 * read by service/repository layers to scope all queries by tenant,
 * and cleared in the filter's {@code finally} block.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_CLIENT_ID = new ThreadLocal<>();

    private TenantContext() {
        // Utility class — no instantiation
    }

    public static void set(UUID clientId) {
        CURRENT_CLIENT_ID.set(clientId);
    }

    public static UUID get() {
        return CURRENT_CLIENT_ID.get();
    }

    public static void clear() {
        CURRENT_CLIENT_ID.remove();
    }
}
