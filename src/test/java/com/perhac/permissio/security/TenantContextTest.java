package com.perhac.permissio.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — TenantContext: ThreadLocal-backed holder for the current client ID.
 */
class TenantContextTest {

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void get_withoutSet_returnsNull() {
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void setAndGet_roundTripsCorrectly() {
        UUID clientId = UUID.randomUUID();
        TenantContext.set(clientId);
        assertThat(TenantContext.get()).isEqualTo(clientId);
    }

    @Test
    void clear_removesTheValue() {
        UUID clientId = UUID.randomUUID();
        TenantContext.set(clientId);
        TenantContext.clear();
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    void set_overwritesPreviousValue() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        TenantContext.set(first);
        TenantContext.set(second);
        assertThat(TenantContext.get()).isEqualTo(second);
    }
}
