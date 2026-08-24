-- Permissio V1: Clients table (tenant registry)
-- Compatible with both H2 (MODE=PostgreSQL) and PostgreSQL
-- UUID generation handled by JPA @GeneratedValue(strategy = GenerationType.UUID)

CREATE TABLE IF NOT EXISTS clients (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
