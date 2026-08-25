#!/usr/bin/env bash
# =============================================================================
# Permissio — Large-Scale Mock Data Generator & Seeder
# Seeds multi-tenant enterprise dataset or dynamically generates N entities
# =============================================================================

set -eo pipefail

SQL_FILE="${SQL_FILE:-perf/large-mock-dataset.sql}"
ENTITY_COUNT=500
MODE="SQL_FILE"

# Parse CLI arguments
for arg in "$@"; do
    case $arg in
        --count=*)
            ENTITY_COUNT="${arg#*=}"
            MODE="DYNAMIC"
            shift
            ;;
        --file=*)
            SQL_FILE="${arg#*=}"
            MODE="SQL_FILE"
            shift
            ;;
        --help)
            echo "Usage: ./scripts/seed-large-dataset.sh [--file=path/to.sql] [--count=N]"
            echo "  --file=path    Load pre-defined SQL dataset (default: perf/large-mock-dataset.sql)"
            echo "  --count=N      Dynamically generate and seed N synthetic subjects/resources/relationships"
            exit 0
            ;;
    esac
done

echo "================================================================="
echo "Permissio — Large-Scale Mock Data Seeder [Mode: $MODE]"
echo "================================================================="

# Ensure PostgreSQL container is running
if ! docker compose ps | grep -q "permissio-postgres"; then
    echo "Starting PostgreSQL container via Docker Compose..."
    docker compose up -d postgres
    sleep 3
fi

if [ "$MODE" = "SQL_FILE" ]; then
    if [ ! -f "$SQL_FILE" ]; then
        echo "[ERROR] SQL seed file not found at: $SQL_FILE"
        exit 1
    fi
    echo "Loading SQL seed file: $SQL_FILE into PostgreSQL..."
    docker compose exec -T postgres psql -U postgres -d permissio < "$SQL_FILE"
    echo "[SUCCESS] Successfully loaded $SQL_FILE into database."
else
    echo "Generating and inserting $ENTITY_COUNT synthetic subjects & resources..."
    GEN_SQL=$(mktemp)
    
    cat << 'EOF' > "$GEN_SQL"
    DO $$
    DECLARE
        v_tenant_id UUID := '11111111-1111-1111-1111-111111111111';
        v_sub_id UUID;
        v_res_id UUID;
        i INT;
    BEGIN
        -- Ensure benchmark tenant exists
        INSERT INTO clients (id, name, api_key_hash, created_at)
        VALUES (v_tenant_id, 'Benchmark Scaled Tenant', '73574c83fb0895318cf1bcf55bcfbf5f939e6a0d6f466c1b3f9dcda3e0c03478', CURRENT_TIMESTAMP)
        ON CONFLICT (id) DO NOTHING;

        FOR i IN 1..COUNT_PLACEHOLDER LOOP
            v_sub_id := gen_random_uuid();
            v_res_id := gen_random_uuid();

            INSERT INTO subjects (id, client_id, external_id, password_hash, attributes, created_at)
            VALUES (v_sub_id, v_tenant_id, 'user_perf_' || i || '_' || v_sub_id, '$2a$10$dXJ3SW6G7P50lGmMkkmwe.20cQQubK3.HZWzG3YB1tlRy.fqvM/BG',
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
    END $$;
EOF

    sed -i "s/COUNT_PLACEHOLDER/$ENTITY_COUNT/g" "$GEN_SQL"
    docker compose exec -T postgres psql -U postgres -d permissio < "$GEN_SQL"
    rm -f "$GEN_SQL"
    echo "[SUCCESS] Generated and inserted $ENTITY_COUNT synthetic subjects, resources, and relationships."
fi

# Print summary counts
echo ""
echo "================================================================="
echo "DATABASE ENTITY COUNTS POST-SEEDING"
echo "================================================================="
docker compose exec -T postgres psql -U postgres -d permissio -c "
SELECT 
    (SELECT count(*) FROM clients) AS tenants_count,
    (SELECT count(*) FROM subjects) AS subjects_count,
    (SELECT count(*) FROM resources) AS resources_count,
    (SELECT count(*) FROM relationships) AS relationships_count,
    (SELECT count(*) FROM policies) AS policies_count,
    (SELECT count(*) FROM audit_logs) AS audit_logs_count;
"
echo "================================================================="
