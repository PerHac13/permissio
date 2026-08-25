# =============================================================================
# Permissio — Large-Scale Mock Data Generator & Seeder (PowerShell)
# =============================================================================

param (
    [string]$File = "perf/large-mock-dataset.sql",
    [int]$Count = 500,
    [switch]$Dynamic
)

$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Permissio — Large-Scale Mock Data Seeder (PowerShell)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# Ensure PostgreSQL container is running
$pgRunning = docker compose ps --services --filter "status=running" | Select-String "postgres"
if (-not $pgRunning) {
    Write-Host "Starting PostgreSQL container via Docker Compose..." -ForegroundColor Yellow
    docker compose up -d postgres
    Start-Sleep -Seconds 3
}

if (-not $Dynamic) {
    if (-not (Test-Path $File)) {
        Write-Error "[ERROR] SQL seed file not found at: $File"
        exit 1
    }
    Write-Host "Loading SQL seed file: $File into PostgreSQL..." -ForegroundColor Yellow
    Get-Content $File -Raw | docker compose exec -T postgres psql -U postgres -d permissio
    Write-Host "[SUCCESS] Successfully loaded $File into database." -ForegroundColor Green
} else {
    Write-Host "Dynamically generating $Count synthetic subjects, resources, and relationships..." -ForegroundColor Yellow
    $genSql = @"
DO `$BODY`$
DECLARE
    v_tenant_id UUID := '11111111-1111-1111-1111-111111111111';
    v_sub_id UUID;
    v_res_id UUID;
    i INT;
BEGIN
    INSERT INTO clients (id, name, api_key_hash, created_at)
    VALUES (v_tenant_id, 'Benchmark Scaled Tenant', '73574c83fb0895318cf1bcf55bcfbf5f939e6a0d6f466c1b3f9dcda3e0c03478', CURRENT_TIMESTAMP)
    ON CONFLICT (id) DO NOTHING;

    FOR i IN 1..$Count LOOP
        v_sub_id := gen_random_uuid();
        v_res_id := gen_random_uuid();

        INSERT INTO subjects (id, client_id, external_id, password_hash, attributes, created_at)
        VALUES (v_sub_id, v_tenant_id, 'user_perf_' || i || '_' || v_sub_id, '`$2a`$10`$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
                jsonb_build_object('department', (ARRAY['engineering','finance','security','hr','operations'])[1 + (i % 5)],
                                   'clearanceLevel', 1 + (i % 5),
                                   'role', (ARRAY['ENGINEER','MANAGER','ANALYST','LEAD','AUDITOR'])[1 + (i % 5)]),
                CURRENT_TIMESTAMP);

        INSERT INTO resources (id, client_id, resource_type, external_id, attributes, created_at)
        VALUES (v_res_id, v_tenant_id, (ARRAY['DOCUMENT','PROJECT','DATASET','INFRASTRUCTURE','TRANSACTION'])[1 + (i % 5)],
                'res_perf_' || i || '_' || v_res_id,
                jsonb_build_object('department', (ARRAY['engineering','finance','security','hr','operations'])[1 + (i % 5)],
                                   'confidentiality', (ARRAY['PUBLIC','INTERNAL','RESTRICTED','TOP_SECRET'])[1 + (i % 4)]),
                CURRENT_TIMESTAMP);

        INSERT INTO relationships (id, client_id, subject_id, resource_id, relation, created_at)
        VALUES (gen_random_uuid(), v_tenant_id, v_sub_id, v_res_id,
                (ARRAY['OWNER','MANAGER','LEAD','MEMBER'])[1 + (i % 4)],
                CURRENT_TIMESTAMP);
    END LOOP;
END `$BODY`$;
"@
    $genSql | docker compose exec -T postgres psql -U postgres -d permissio
    Write-Host "[SUCCESS] Generated and inserted $Count synthetic entities." -ForegroundColor Green
}

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "DATABASE ENTITY COUNTS POST-SEEDING" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan
docker compose exec -T postgres psql -U postgres -d permissio -c "
SELECT 
    (SELECT count(*) FROM clients) AS tenants_count,
    (SELECT count(*) FROM subjects) AS subjects_count,
    (SELECT count(*) FROM resources) AS resources_count,
    (SELECT count(*) FROM relationships) AS relationships_count,
    (SELECT count(*) FROM policies) AS policies_count,
    (SELECT count(*) FROM audit_logs) AS audit_logs_count;
"
Write-Host "=================================================================" -ForegroundColor Cyan
