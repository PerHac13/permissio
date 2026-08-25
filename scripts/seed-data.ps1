# =============================================================================
# Permissio — Mock Seed Data Loader (PowerShell)
# =============================================================================

$ErrorActionPreference = "Stop"

Write-Host "Seeding Permissio PostgreSQL database with mock dataset..." -ForegroundColor Cyan

Get-Content "perf/mock-seed-data.sql" | docker compose exec -T postgres psql -U postgres -d permissio

Write-Host "Successfully loaded mock seed data into PostgreSQL." -ForegroundColor Green
Write-Host "Tenants created: Acme Corporation, CyberDyne Systems, Stark Dynamics" -ForegroundColor Green
