-- Permissio V5: Policies table (tenant-scoped ABAC and Business Rule policy expressions)
-- Compatible with both H2 (MODE=PostgreSQL) and PostgreSQL
-- UUID generation handled by JPA @GeneratedValue(strategy = GenerationType.UUID)

CREATE TABLE IF NOT EXISTS policies (
    id UUID PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id),
    resource_type VARCHAR(100) NOT NULL,
    action VARCHAR(50) NOT NULL,
    policy_type VARCHAR(50) NOT NULL,
    expression TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_policies_client_id ON policies(client_id);
CREATE INDEX IF NOT EXISTS idx_policies_client_lookup ON policies(client_id, resource_type, action, policy_type);
