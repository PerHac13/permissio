#!/usr/bin/env bash
# =============================================================================
# Permissio — Mock Seed Data Loader
# Loads perf/mock-seed-data.sql into PostgreSQL Docker container
# =============================================================================

set -eo pipefail

echo "Seeding Permissio PostgreSQL database with mock dataset..."

if ! docker compose ps | grep -q "permissio-postgres"; then
    echo "Postgres container is not running. Starting Docker Compose..."
    docker compose up -d postgres
    sleep 3
fi

docker compose exec -T postgres psql -U postgres -d permissio < perf/mock-seed-data.sql

echo "Successfully loaded mock seed data into PostgreSQL."
echo "Tenants created: Acme Corporation, CyberDyne Systems, Stark Dynamics"
echo "Subjects, resources, ReBAC relations, and ABAC policies are now active."
