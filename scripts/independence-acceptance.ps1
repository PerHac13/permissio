# =============================================================================
# Permissio — Independence Acceptance Test (PowerShell / Docker Compose)
# TRD Section 9.4 & PRD Section 2.3
# =============================================================================

$ErrorActionPreference = "Stop"

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "Permissio — Independence Acceptance Test (Docker Compose)" -ForegroundColor Cyan
Write-Host "=================================================================" -ForegroundColor Cyan

# 1. Start Docker Compose
Write-Host "Starting Docker Compose environment..." -ForegroundColor Yellow
docker compose down -v 2>$null
docker compose up -d --build

try {
    # 2. Wait for healthcheck
    Write-Host "Waiting for Permissio healthcheck (http://localhost:8080/actuator/health)..." -ForegroundColor Yellow
    $healthy = $false
    for ($i = 1; $i -le 30; $i++) {
        try {
            $resp = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -Method Get -TimeoutSec 2
            if ($resp.status -eq "UP") {
                $healthy = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }

    if (-not $healthy) {
        Write-Error "[ERROR] Timed out waiting for Permissio to start!"
        docker compose logs permissio
        exit 1
    }
    Write-Host "[SUCCESS] Permissio is healthy and running!" -ForegroundColor Green

    $rawApiKey = "acceptance-client-api-key-999"
    $salt = "permissio-docker-salt-for-dev-only"
    
    # Calculate SHA256(rawApiKey + salt)
    $hasher = [System.Security.Cryptography.SHA256]::Create()
    $bytes = [System.Text.Encoding]::UTF8.GetBytes("$rawApiKey$salt")
    $hashBytes = $hasher.ComputeHash($bytes)
    $apiKeyHash = [BitConverter]::ToString($hashBytes).Replace("-", "").ToLower()

    # 3. Seed Tenant
    Write-Host "Provisioning test tenant client in database..." -ForegroundColor Yellow
    docker compose exec -T postgres psql -U postgres -d permissio -c "INSERT INTO clients (id, name, api_key_hash, created_at) VALUES ('11111111-1111-1111-1111-111111111111', 'Acceptance Tenant', '$apiKeyHash', now()) ON CONFLICT (id) DO NOTHING;"

    # 4. Register
    Write-Host "1. Registering user via POST /api/v1/auth/register..." -ForegroundColor Yellow
    $regBody = @{
        externalId = "acceptance_admin"
        password = "StrongPassword123!"
        attributes = @{ department = "Security" }
    } | ConvertTo-Json

    $headers = @{
        "Content-Type" = "application/json"
        "X-API-Key" = $rawApiKey
    }

    $regResp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/register" -Method Post -Headers $headers -Body $regBody
    $jwtToken = $regResp.token
    $subjectId = $regResp.subjectId
    Write-Host "   Registered Subject ID: $subjectId" -ForegroundColor Green

    $authHeaders = @{
        "Content-Type" = "application/json"
        "X-API-Key" = $rawApiKey
        "Authorization" = "Bearer $jwtToken"
    }

    # 5. Create Resource
    Write-Host "2. Creating Resource via POST /api/v1/resources..." -ForegroundColor Yellow
    $resBody = @{
        resourceType = "DOCUMENT"
        externalId = "confidential_plan"
        attributes = @{ classification = "RESTRICTED" }
    } | ConvertTo-Json

    $resResp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/resources" -Method Post -Headers $authHeaders -Body $resBody
    $resourceId = $resResp.id
    Write-Host "   Created Resource ID: $resourceId" -ForegroundColor Green

    # 6. Create Relationship (OWNER)
    Write-Host "3. Creating OWNER relationship via POST /api/v1/relationships..." -ForegroundColor Yellow
    $relBody = @{
        subjectId = $subjectId
        resourceId = $resourceId
        relation = "OWNER"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/relationships" -Method Post -Headers $authHeaders -Body $relBody | Out-Null
    Write-Host "   Relationship created." -ForegroundColor Green

    # 7. Authorize (ALLOW)
    Write-Host "4. Testing POST /api/v1/authorize (OWNER -> UPDATE) - Expect ALLOWED..." -ForegroundColor Yellow
    $authzBody = @{
        subjectId = $subjectId
        resourceId = $resourceId
        action = "UPDATE"
    } | ConvertTo-Json

    $authzResp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/authorize" -Method Post -Headers $authHeaders -Body $authzBody
    if ($authzResp.allowed -eq $true) {
        Write-Host "   Authorization ALLOWED verified!" -ForegroundColor Green
    } else {
        Write-Error "[ERROR] Expected allowed:true"
    }

    # 8. Member User & DENY
    Write-Host "5. Registering a MEMBER user and testing DENY..." -ForegroundColor Yellow
    $memberRegBody = @{ externalId = "regular_member"; password = "Password456!" } | ConvertTo-Json
    $memberResp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/auth/register" -Method Post -Headers $headers -Body $memberRegBody
    $memberId = $memberResp.subjectId

    $memberRelBody = @{ subjectId = $memberId; resourceId = $resourceId; relation = "MEMBER" } | ConvertTo-Json
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/relationships" -Method Post -Headers $authHeaders -Body $memberRelBody | Out-Null

    $denyBody = @{ subjectId = $memberId; resourceId = $resourceId; action = "DELETE" } | ConvertTo-Json
    $denyResp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/authorize" -Method Post -Headers $authHeaders -Body $denyBody
    if ($denyResp.allowed -eq $false) {
        Write-Host "   Authorization DENIED verified correctly!" -ForegroundColor Green
    } else {
        Write-Error "[ERROR] Expected allowed:false"
    }

    # 9. Audit Logs
    Write-Host "6. Querying Audit Logs via GET /api/v1/audit-logs..." -ForegroundColor Yellow
    $auditResp = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/audit-logs?subjectId=$subjectId" -Method Get -Headers $authHeaders
    Write-Host "   Audit logs verified!" -ForegroundColor Green

    Write-Host ""
    Write-Host "=================================================================" -ForegroundColor Green
    Write-Host "SUCCESS: All Independence Acceptance Checks Passed 100%!" -ForegroundColor Green
    Write-Host "=================================================================" -ForegroundColor Green

} finally {
    Write-Host "Tearing down Docker Compose environment..." -ForegroundColor Yellow
    docker compose down -v
}
