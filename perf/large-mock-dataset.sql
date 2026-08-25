-- =============================================================================
-- Permissio: Large-Scale Enterprise Multi-Tenant Mock Dataset
-- 5 Distinct Industry Tenants with High-Cardinality Domain Entities,
-- ReBAC Hierarchical Networks, and Complex Dynamic ABAC Policies.
-- Compatible with PostgreSQL 16 and H2 (PostgreSQL compatibility mode).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. TENANT CLIENTS (5 Enterprise Domains)
-- -----------------------------------------------------------------------------
-- Tenant 1: Acme FinTech          (API Key: 'acme-perf-api-key-111')
-- Tenant 2: MediHealth Systems    (API Key: 'medihealth-perf-api-key-222')
-- Tenant 3: Stark Defense         (API Key: 'stark-defense-perf-api-key-333')
-- Tenant 4: Global Retail         (API Key: 'global-retail-perf-api-key-444')
-- Tenant 5: CloudSaaS Platform    (API Key: 'cloudsaas-perf-api-key-555')

INSERT INTO clients (id, name, api_key_hash, created_at)
VALUES 
('11111111-1111-1111-1111-111111111111', 'Acme FinTech Group', '73574c83fb0895318cf1bcf55bcfbf5f939e6a0d6f466c1b3f9dcda3e0c03478', CURRENT_TIMESTAMP),
('22222222-2222-2222-2222-222222222222', 'MediHealth Healthcare', '84685d94ac1906429df2cdf66cd0cf60a40f7b1e70577d2c400edeb4f1d14589', CURRENT_TIMESTAMP),
('33333333-3333-3333-3333-333333333333', 'Stark Defense Technologies', '95796ea5bd2017530ef3dee77de1df71b5108c2f81688e3d511feec502e25690', CURRENT_TIMESTAMP),
('44444444-4444-4444-4444-444444444444', 'Global Retail E-Commerce', 'a68a7fb6ce3128641fa4eff88ef2ef82c6219d3f92799f4e6220ff0613f36701', CURRENT_TIMESTAMP),
('55555555-5555-5555-5555-555555555555', 'CloudSaaS Platform Infrastructure', 'b79b80c7df42397520b5f0099f03f093d732ae40a38aa05f7331001724047812', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 2. SUBJECTS (High-Cardinality Role-Diverse Actors)
-- -----------------------------------------------------------------------------
-- Default Password: 'Password123!' (BCrypt Hash: $2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG)

-- [Tenant 1: Acme FinTech Group]
INSERT INTO subjects (id, client_id, external_id, password_hash, attributes, created_at)
VALUES
('11111111-0001-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'alice.vp@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "engineering", "clearanceLevel": 5, "role": "VP_ENGINEERING", "region": "US-EAST", "employmentType": "FULL_TIME"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'bob.lead@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "engineering", "clearanceLevel": 4, "role": "TECH_LEAD", "region": "US-WEST", "employmentType": "FULL_TIME"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'charlie.dev@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "engineering", "clearanceLevel": 2, "role": "SOFTWARE_ENGINEER", "region": "EU-CENTRAL", "employmentType": "FULL_TIME"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'diana.cfo@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "finance", "clearanceLevel": 5, "role": "CFO", "region": "US-EAST", "employmentType": "EXECUTIVE"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', 'evan.trader@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "finance", "clearanceLevel": 4, "role": "QUANT_TRADER", "region": "UK-LONDON", "employmentType": "FULL_TIME"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111', 'fiona.auditor@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "compliance", "clearanceLevel": 5, "role": "EXTERNAL_AUDITOR", "region": "GLOBAL", "employmentType": "CONTRACTOR"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000007', '11111111-1111-1111-1111-111111111111', 'george.secops@acme.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "security", "clearanceLevel": 5, "role": "SECOPS_LEAD", "region": "US-EAST", "employmentType": "FULL_TIME"}', CURRENT_TIMESTAMP),
('11111111-0001-0000-0000-000000000008', '11111111-1111-1111-1111-111111111111', 'svc.auto.settlement', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "fintech_bot", "clearanceLevel": 4, "role": "SERVICE_ACCOUNT", "type": "AUTOMATION"}', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- [Tenant 2: MediHealth Healthcare]
INSERT INTO subjects (id, client_id, external_id, password_hash, attributes, created_at)
VALUES
('22222222-0001-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'dr.house.chief@medihealth.org', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "diagnostics", "clearanceLevel": 5, "role": "CHIEF_PHYSICIAN", "license": "MD_99482"}', CURRENT_TIMESTAMP),
('22222222-0001-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'dr.cameron.cardio@medihealth.org', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "cardiology", "clearanceLevel": 4, "role": "ATTENDING_PHYSICIAN", "license": "MD_77361"}', CURRENT_TIMESTAMP),
('22222222-0001-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'nurse.jackie@medihealth.org', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "emergency", "clearanceLevel": 3, "role": "TRIAGE_NURSE", "shift": "NIGHT"}', CURRENT_TIMESTAMP),
('22222222-0001-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', 'admin.hipaa.officer@medihealth.org', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "compliance", "clearanceLevel": 5, "role": "HIPAA_OFFICER", "status": "CERTIFIED"}', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- [Tenant 3: Stark Defense Technologies]
INSERT INTO subjects (id, client_id, external_id, password_hash, attributes, created_at)
VALUES
('33333333-0001-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', 'tony.stark.ceo@defense.stark.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "advanced_r_and_d", "clearanceLevel": 5, "role": "COMMANDER", "securityClearance": "COSMIC_TOP_SECRET"}', CURRENT_TIMESTAMP),
('33333333-0001-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 'col.rhodes.military@defense.stark.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "military_liaison", "clearanceLevel": 5, "role": "GENERAL", "securityClearance": "DEFENSE_TOP_SECRET"}', CURRENT_TIMESTAMP),
('33333333-0001-0000-0000-000000000003', '33333333-3333-3333-3333-333333333333', 'engineer.jarvis.ai@defense.stark.com', '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG', '{"department": "ai_systems", "clearanceLevel": 5, "role": "AUTONOMOUS_CONTROLLER", "type": "AI_SYSTEM"}', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 3. RESOURCES (Diverse Domain Assets & Classifications)
-- -----------------------------------------------------------------------------

-- [Tenant 1: Acme FinTech Group]
INSERT INTO resources (id, client_id, resource_type, external_id, attributes, created_at)
VALUES
('11111111-0002-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'PORTFOLIO', 'portfolio_hft_alpha_99', '{"department": "finance", "confidentiality": "TOP_SECRET", "classification": "RESTRICTED", "ownerTeam": "finance"}', CURRENT_TIMESTAMP),
('11111111-0002-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'LEDGER_ACCOUNT', 'acct_swift_settlement_01', '{"department": "finance", "currency": "USD", "complianceTier": "SOC2_TYPE2"}', CURRENT_TIMESTAMP),
('11111111-0002-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'DOCUMENT', 'doc_fintech_compliance_audit_2026', '{"department": "compliance", "confidentiality": "RESTRICTED", "ownerTeam": "compliance"}', CURRENT_TIMESTAMP),
('11111111-0002-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', 'INFRASTRUCTURE', 'cluster_prod_vault_east', '{"department": "security", "environment": "PRODUCTION", "criticality": "TIER_0"}', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- [Tenant 2: MediHealth Healthcare]
INSERT INTO resources (id, client_id, resource_type, external_id, attributes, created_at)
VALUES
('22222222-0002-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'PATIENT_EHR', 'ehr_patient_record_77492', '{"department": "diagnostics", "hipaaClass": "PHI_PROTECTED", "confidentiality": "STRICT_RESTRICTED", "ownerDoctor": "dr.house.chief@medihealth.org"}', CURRENT_TIMESTAMP),
('22222222-0002-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'PRESCRIPTION', 'rx_controlled_substance_091', '{"department": "pharmacy", "schedule": "SCHEDULE_II", "requiresDualSign": "true"}', CURRENT_TIMESTAMP),
('22222222-0002-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', 'ICU_MONITOR', 'device_icu_cardio_bed_4', '{"department": "emergency", "deviceStatus": "LIVE", "criticality": "LIFE_SUPPORT"}', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- [Tenant 3: Stark Defense Technologies]
INSERT INTO resources (id, client_id, resource_type, external_id, attributes, created_at)
VALUES
('33333333-0002-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', 'WEAPON_SYSTEM', 'mark_85_flight_telemetry', '{"department": "advanced_r_and_d", "confidentiality": "COSMIC_TOP_SECRET", "clearanceRequired": 5}', CURRENT_TIMESTAMP),
('33333333-0002-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 'SATELLITE_UPLINK', 'orbital_defense_grid_sat_3', '{"department": "military_liaison", "protocol": "QUANTUM_ENCRYPTED", "status": "ACTIVE"}', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 4. RELATIONSHIPS (ReBAC Hierarchical Tuples)
-- -----------------------------------------------------------------------------

-- Acme FinTech ReBAC Tuples
INSERT INTO relationships (id, client_id, subject_id, resource_id, relation, created_at)
VALUES
('11111111-0003-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000004', '11111111-0002-0000-0000-000000000001', 'OWNER', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000005', '11111111-0002-0000-0000-000000000001', 'MANAGER', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000006', '11111111-0002-0000-0000-000000000003', 'LEAD', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000007', '11111111-0002-0000-0000-000000000004', 'OWNER', CURRENT_TIMESTAMP),
('11111111-0003-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111', '11111111-0001-0000-0000-000000000008', '11111111-0002-0000-0000-000000000002', 'MANAGER', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- MediHealth ReBAC Tuples
INSERT INTO relationships (id, client_id, subject_id, resource_id, relation, created_at)
VALUES
('22222222-0003-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', '22222222-0001-0000-0000-000000000001', '22222222-0002-0000-0000-000000000001', 'OWNER', CURRENT_TIMESTAMP),
('22222222-0003-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', '22222222-0001-0000-0000-000000000002', '22222222-0002-0000-0000-000000000001', 'MANAGER', CURRENT_TIMESTAMP),
('22222222-0003-0000-0000-000000000003', '22222222-2222-2222-2222-222222222222', '22222222-0001-0000-0000-000000000003', '22222222-0002-0000-0000-000000000001', 'MEMBER', CURRENT_TIMESTAMP),
('22222222-0003-0000-0000-000000000004', '22222222-2222-2222-2222-222222222222', '22222222-0001-0000-0000-000000000001', '22222222-0002-0000-0000-000000000002', 'OWNER', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Stark Defense ReBAC Tuples
INSERT INTO relationships (id, client_id, subject_id, resource_id, relation, created_at)
VALUES
('33333333-0003-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', '33333333-0001-0000-0000-000000000001', '33333333-0002-0000-0000-000000000001', 'OWNER', CURRENT_TIMESTAMP),
('33333333-0003-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', '33333333-0001-0000-0000-000000000002', '33333333-0002-0000-0000-000000000001', 'LEAD', CURRENT_TIMESTAMP),
('33333333-0003-0000-0000-000000000003', '33333333-3333-3333-3333-333333333333', '33333333-0001-0000-0000-000000000003', '33333333-0002-0000-0000-000000000002', 'OWNER', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- -----------------------------------------------------------------------------
-- 5. POLICIES (Dynamic ABAC & Business Rules)
-- -----------------------------------------------------------------------------

-- Acme FinTech ABAC Policies
INSERT INTO policies (id, client_id, resource_type, action, policy_type, expression, created_at)
VALUES
('11111111-0004-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111', 'PORTFOLIO', 'UPDATE', 'ABAC', '#subject.attributes[''department''] == #resource.attributes[''department''] and #subject.attributes[''clearanceLevel''] >= 4', CURRENT_TIMESTAMP),
('11111111-0004-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'INFRASTRUCTURE', 'DELETE', 'ABAC', '#subject.attributes[''role''] == ''SECOPS_LEAD'' and #subject.attributes[''clearanceLevel''] == 5', CURRENT_TIMESTAMP),
('11111111-0004-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'PORTFOLIO', 'READ', 'BUSINESS_RULE', '#environment[''maintenanceWindow''] != true', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- MediHealth ABAC Policies
INSERT INTO policies (id, client_id, resource_type, action, policy_type, expression, created_at)
VALUES
('22222222-0004-0000-0000-000000000001', '22222222-2222-2222-2222-222222222222', 'PATIENT_EHR', 'READ', 'ABAC', '#subject.attributes[''clearanceLevel''] >= 3 and (#subject.attributes[''department''] == #resource.attributes[''department''] or #subject.attributes[''role''] == ''HIPAA_OFFICER'')', CURRENT_TIMESTAMP),
('22222222-0004-0000-0000-000000000002', '22222222-2222-2222-2222-222222222222', 'PRESCRIPTION', 'APPROVE', 'ABAC', '#subject.attributes[''role''] == ''CHIEF_PHYSICIAN'' or #subject.attributes[''role''] == ''ATTENDING_PHYSICIAN''', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- Stark Defense ABAC Policies
INSERT INTO policies (id, client_id, resource_type, action, policy_type, expression, created_at)
VALUES
('33333333-0004-0000-0000-000000000001', '33333333-3333-3333-3333-333333333333', 'WEAPON_SYSTEM', 'UPDATE', 'ABAC', '#subject.attributes[''clearanceLevel''] >= 5 and #subject.attributes[''securityClearance''] == ''COSMIC_TOP_SECRET''', CURRENT_TIMESTAMP),
('33333333-0004-0000-0000-000000000002', '33333333-3333-3333-3333-333333333333', 'SATELLITE_UPLINK', 'CREATE', 'ABAC', '#subject.attributes[''role''] == ''AUTONOMOUS_CONTROLLER'' or #subject.attributes[''role''] == ''COMMANDER''', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
