-- Permissio V4: Relationships table (tenant-scoped ReBAC tuples)
-- Compatible with both H2 (MODE=PostgreSQL) and PostgreSQL

CREATE TABLE IF NOT EXISTS relationships (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id),
    subject_id UUID NOT NULL REFERENCES subjects(id),
    resource_id UUID NOT NULL REFERENCES resources(id),
    relation VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_relationships_client_sub_res_rel UNIQUE (client_id, subject_id, resource_id, relation)
);

CREATE INDEX IF NOT EXISTS idx_relationships_client_id ON relationships(client_id);
CREATE INDEX IF NOT EXISTS idx_relationships_client_subject ON relationships(client_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_relationships_client_resource ON relationships(client_id, resource_id);
CREATE INDEX IF NOT EXISTS idx_relationships_client_sub_res ON relationships(client_id, subject_id, resource_id);
