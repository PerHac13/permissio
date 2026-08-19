-- Permissio V6: Audit Logs table (durable, tenant-scoped authorization decision logs)
-- Compatible with both H2 (MODE=PostgreSQL) and PostgreSQL

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID DEFAULT RANDOM_UUID() PRIMARY KEY,
    client_id UUID NOT NULL REFERENCES clients(id),
    subject_id UUID,
    resource_id UUID,
    action VARCHAR(50) NOT NULL,
    allowed BOOLEAN NOT NULL,
    reason VARCHAR(100),
    evaluator VARCHAR(100),
    trace_id VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_client_created ON audit_logs(client_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_client_subject ON audit_logs(client_id, subject_id);
CREATE INDEX IF NOT EXISTS idx_audit_client_resource ON audit_logs(client_id, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_trace_id ON audit_logs(trace_id);
