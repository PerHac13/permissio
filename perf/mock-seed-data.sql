-- =============================================================================
-- Permissio: Comprehensive Mock & Benchmark Seed Dataset
-- Seeds 3 enterprise tenant clients with subjects, resources, ReBAC relations,
-- and ABAC policies for performance benchmarking and smoke tests.
-- Compatible with PostgreSQL 16 and H2 (PostgreSQL compatibility mode).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. TENANT CLIENTS
-- -----------------------------------------------------------------------------
-- Client 1: Acme Corporation (API Key: 'acme-perf-api-key-111')
-- Client 2: CyberDyne Systems (API Key: 'cyber-perf-api-key-222')
-- Client 3: Stark Dynamics (API Key: 'stark-perf-api-key-333')

INSERT INTO clients (id, name, api_key_hash, created_at)
VALUES 
('11111111-1111-1111-1111-111111111111', 'Acme Corporation', '73574c83fb0895318cf1bcf55bcfbf5f939e6a0d6f466c1b3f9dcda3e0c03478', CURRENT_TIMESTAMP),
('22222222-2222-2222-2222-222222222222', 'CyberDyne Systems', '84685d94ac1906429df2cdf66cd0cf60a40f7b1e70577d2c400edeb4f1d14589', CURRENT_TIMESTAMP),
('33333333-3333-3333-3333-333333333333', 'Stark Dynamics', '95796ea5bd2017530ef3dee77de1df71b5108c2f81688e3d511feec502e25690', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. SUBJECTS (Acme Corporation - Client 1)
-- -----------------------------------------------------------------------------
-- Password hash: 'Password123!' (BCrypt)
INSERT INTO subjects (id, client_id, external_id, password_hash, attributes, created_at)
VALUES
('11111111-0001-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'alice.vp@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "engineering", "clearanceLevel": 5, "role": "VP_ENGINEERING", "region": "US-EAST"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'bob.lead@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "engineering", "clearanceLevel": 4, "role": "TECH_LEAD", "region": "US-WEST"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'charlie.dev@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "engineering", "clearanceLevel": 2, "role": "SOFTWARE_ENGINEER", "region": "EU-CENTRAL"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'diana.cfo@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "finance", "clearanceLevel": 5, "role": "CFO", "region": "US-EAST"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'evan.analyst@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "finance", "clearanceLevel": 3, "role": "FINANCIAL_ANALYST", "region": "US-EAST"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'fiona.hr@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "hr", "clearanceLevel": 4, "role": "HR_DIRECTOR", "region": "US-WEST"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111', 'george.sec@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "security", "clearanceLevel": 5, "role": "CISO", "region": "GLOBAL"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000008', '11111111-1111-1111-1111-111111111111', 'svc.ci.runner', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "devops", "clearanceLevel": 5, "role": "SERVICE_ACCOUNT", "type": "AUTOMATION"}', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3. RESOURCES (Acme Corporation - Client 1)
-- -----------------------------------------------------------------------------
INSERT INTO resources (id, client_id, resource_type, external_id, attributes, created_at)
VALUES
('11111111-0002-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'DOCUMENT', 'doc_tech_roadmap_2026', '{"department": "engineering", "confidentiality": "HIGH", "classification": "RESTRICTED", "ownerTeam": "engineering"}', CURRENT_TIMESTAMP),
('11111111-0002-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'DOCUMENT', 'doc_q3_financial_statement', '{"department": "finance", "confidentiality": "TOP_SECRET", "classification": "RESTRICTED", "ownerTeam": "finance"}', CURRENT_TIMESTAMP),
('11111111-0002-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'DOCUMENT', 'doc_employee_handbook', '{"department": "hr", "confidentiality": "PUBLIC", "classification": "UNCLASSIFIED", "ownerTeam": "hr"}', CURRENT_TIMESTAMP),
('11111111-0002-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'PROJECT', 'proj_auth_engine_v2', '{"department": "engineering", "status": "ACTIVE", "tier": "TIER_1"}', CURRENT_TIMESTAMP),
('11111111-0002-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'INFRASTRUCTURE', 'k8s_production_cluster', '{"department": "security", "environment": "PRODUCTION", "criticality": "HIGH"}', CURRENT_TIMESTAMP),
('11111111-0002-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'BILLING_ACCOUNT', 'acct_enterprise_master', '{"department": "finance", "currency": "USD", "status": "OPEN"}', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. RELATIONSHIPS (ReBAC Tuples - Client 1)
-- -----------------------------------------------------------------------------
INSERT INTO relationships (id, client_id, subject_id, resource_id, relation, created_at)
VALUES
('11111111-0003-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000001', '11111111-0002-0000-0000-000000000001', 'OWNER', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000002', '11111111-0002-0000-0000-000000000001', 'MANAGER', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000003', '11111111-0002-0000-0000-000000000001', 'MEMBER', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000004', '11111111-0002-0000-0000-000000000002', 'OWNER', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000005', '11111111-0002-0000-0000-000000000002', 'LEAD', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000006', '11111111-0002-0000-0000-000000000003', 'OWNER', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000001', '11111111-0002-0000-0000-000000000004', 'OWNER', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000008', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000002', '11111111-0002-0000-0000-000000000004', 'LEAD', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000009', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000007', '11111111-0002-0000-0000-000000000005', 'OWNER', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000010', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000008', '11111111-0002-0000-0000-000000000005', 'MANAGER', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. POLICIES (SpEL ABAC & Business Rules - Client 1)
-- -----------------------------------------------------------------------------
INSERT INTO policies (id, client_id, resource_type, action, policy_type, expression)
VALUES
('11111111-0004-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'DOCUMENT', 'UPDATE', 'ABAC', '#subject.attributes[''department''] == #resource.attributes[''ownerTeam'']'),
('11111111-0004-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'DOCUMENT', 'DELETE', 'ABAC', '#subject.attributes[''clearanceLevel''] >= 4')
ON CONFLICT (id) DO NOTHING;
