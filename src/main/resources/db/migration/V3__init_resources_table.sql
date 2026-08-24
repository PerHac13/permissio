-- Permissio V3: Resources table (tenant-scoped generic resource registry)
-- Compatible with both H2 (MODE=PostgreSQL) and PostgreSQL
-- UUID generation handled by JPA @GeneratedValue(strategy = GenerationType.UUID)

CREATE TABLE IF NOT EXISTS resources (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id),
    resource_type VARCHAR(100) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    attributes TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_resources_client_type_external UNIQUE (client_id, resource_type, external_id)
);

CREATE INDEX IF NOT EXISTS idx_resources_client_id ON resources(client_id);
CREATE INDEX IF NOT EXISTS idx_resources_client_type_external ON resources(client_id, resource_type, external_id);
