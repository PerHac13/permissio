-- Permissio V1: Clients table (tenant registry)
-- Compatible with both H2 (MODE=PostgreSQL) and PostgreSQL

CREATE TABLE IF NOT EXISTS clients (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    api_key_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
