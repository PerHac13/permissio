-- Permissio V2: Subjects table (user/actor registry within tenants)
-- Compatible with both H2 (MODE=PostgreSQL) and PostgreSQL

CREATE TABLE IF NOT EXISTS subjects (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id),
    external_id VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    attributes TEXT NOT NULL DEFAULT '{}',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_subjects_client_external UNIQUE (client_id, external_id)
);

CREATE INDEX IF NOT EXISTS idx_subjects_client_external ON subjects(client_id, external_id);
