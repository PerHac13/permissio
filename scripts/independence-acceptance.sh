#!/usr/bin/env bash
# =============================================================================
# Permissio — Independence Acceptance Test (Docker Compose)
# TRD Section 9.4 & PRD Section 2.3
#
# Spins up Permissio + Postgres + OTel Collector in Docker, registers a tenant,
# provisions subjects/resources/relationships, tests the /authorize engine,
# and verifies audit logs and OpenAPI docs with zero external dependencies.
# =============================================================================

set -eo pipefail

echo "================================================================="
echo "Permissio — Independence Acceptance Test (Docker Compose)"
echo "================================================================="

# Generate ephemeral RSA key pair for testing if not provided
if [ -z "$PERMISSIO_JWT_PRIVATE_KEY" ] || [ -z "$PERMISSIO_JWT_PUBLIC_KEY" ]; then
    echo "Generating ephemeral RSA 2048-bit key pair..."
    TEMP_DIR=$(mktemp -d)
    openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$TEMP_DIR/priv.pem" 2>/dev/null
    openssl rsa -pubout -in "$TEMP_DIR/priv.pem" -out "$TEMP_DIR/pub.pem" 2>/dev/null
    
    export PERMISSIO_JWT_PRIVATE_KEY=$(grep -v -- "-----" "$TEMP_DIR/priv.pem" | tr -d '\r\n')
    export PERMISSIO_JWT_PUBLIC_KEY=$(grep -v -- "-----" "$TEMP_DIR/pub.pem" | tr -d '\r\n')
    rm -rf "$TEMP_DIR"
fi

export PERMISSIO_API_KEY_SALT="${PERMISSIO_API_KEY_SALT:-acceptance-test-salt-12345}"
RAW_API_KEY="acceptance-client-api-key-999"

# Calculate salted SHA-256 hash of API key (same as ApiKeyHasher: SHA-256(rawApiKey + salt))
API_KEY_HASH=$(echo -n "${RAW_API_KEY}${PERMISSIO_API_KEY_SALT}" | sha256sum | awk '{print $1}')

echo "Starting Docker Compose environment..."
docker compose down -v 2>/dev/null || true
docker compose up -d --build

cleanup() {
    EXIT_CODE=$?
    if [ $EXIT_CODE -ne 0 ]; then
        echo ""
        echo "================================================================="
        echo "[DEBUG] ACCEPTANCE TEST FAILED (Exit Code: $EXIT_CODE)"
        echo "================================================================="
        echo "Dumping Docker Compose Container Logs:"
        docker compose logs --tail=150
        echo "================================================================="
    fi
    echo ""
    echo "Tearing down Docker Compose environment..."
    docker compose down -v
}
trap cleanup EXIT

echo "Waiting for Permissio healthcheck (http://localhost:8080/actuator/health)..."
MAX_ATTEMPTS=45
ATTEMPT=0
until curl -s -f http://localhost:8080/actuator/health | grep -q "UP"; do
    ATTEMPT=$((ATTEMPT + 1))
    if [ $ATTEMPT -ge $MAX_ATTEMPTS ]; then
        echo "[ERROR] Timed out waiting for Permissio to start!"
        exit 1
    fi
    sleep 2
done
echo "[SUCCESS] Permissio is healthy and running!"

echo "Provisioning test tenant client in database..."
docker compose exec -T postgres psql -U postgres -d permissio -c \
    "INSERT INTO clients (id, name, api_key_hash, created_at) VALUES ('11111111-1111-1111-1111-111111111111', 'Acceptance Tenant', '${API_KEY_HASH}', now()) ON CONFLICT (id) DO NOTHING;"

echo "1. Registering user via POST /api/v1/auth/register..."
REGISTER_RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${RAW_API_KEY}" \
    -d '{"externalId":"acceptance_admin","password":"StrongPassword123!","attributes":{"department":"Security"}}')

JWT_TOKEN=$(echo "$REGISTER_RESP" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
SUBJECT_ID=$(echo "$REGISTER_RESP" | grep -o '"subjectId":"[^"]*' | cut -d'"' -f4)

if [ -z "$JWT_TOKEN" ] || [ -z "$SUBJECT_ID" ]; then
    echo "[ERROR] Failed to register user! Response: $REGISTER_RESP"
    exit 1
fi
echo "   Registered Subject ID: $SUBJECT_ID"

echo "2. Creating Resource via POST /api/v1/resources..."
RESOURCE_RESP=$(curl -s -X POST http://localhost:8080/api/v1/resources \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${RAW_API_KEY}" \
    -H "Authorization: Bearer ${JWT_TOKEN}" \
    -d '{"resourceType":"DOCUMENT","externalId":"confidential_plan","attributes":{"classification":"RESTRICTED"}}')

RESOURCE_ID=$(echo "$RESOURCE_RESP" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [ -z "$RESOURCE_ID" ]; then
    echo "[ERROR] Failed to create resource! Response: $RESOURCE_RESP"
    exit 1
fi
echo "   Created Resource ID: $RESOURCE_ID"

echo "3. Creating OWNER relationship via POST /api/v1/relationships..."
REL_RESP=$(curl -s -X POST http://localhost:8080/api/v1/relationships \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${RAW_API_KEY}" \
    -H "Authorization: Bearer ${JWT_TOKEN}" \
    -d "{\"subjectId\":\"${SUBJECT_ID}\",\"resourceId\":\"${RESOURCE_ID}\",\"relation\":\"OWNER\"}")
echo "   Relationship created."

echo "4. Testing POST /api/v1/authorize (OWNER -> UPDATE) - Expect ALLOWED..."
AUTHZ_ALLOW=$(curl -s -X POST http://localhost:8080/api/v1/authorize \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${RAW_API_KEY}" \
    -H "Authorization: Bearer ${JWT_TOKEN}" \
    -d "{\"subjectId\":\"${SUBJECT_ID}\",\"resourceId\":\"${RESOURCE_ID}\",\"action\":\"UPDATE\"}")

if echo "$AUTHZ_ALLOW" | grep -q '"allowed":true'; then
    echo "   Authorization ALLOWED verified!"
else
    echo "[ERROR] Expected allowed:true but got: $AUTHZ_ALLOW"
    exit 1
fi

echo "5. Registering a MEMBER user..."
MEMBER_RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/register \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${RAW_API_KEY}" \
    -d '{"externalId":"regular_member","password":"Password456!"}')

MEMBER_ID=$(echo "$MEMBER_RESP" | grep -o '"subjectId":"[^"]*' | cut -d'"' -f4)

curl -s -X POST http://localhost:8080/api/v1/relationships \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${RAW_API_KEY}" \
    -H "Authorization: Bearer ${JWT_TOKEN}" \
    -d "{\"subjectId\":\"${MEMBER_ID}\",\"resourceId\":\"${RESOURCE_ID}\",\"relation\":\"MEMBER\"}" >/dev/null

echo "6. Testing POST /api/v1/authorize (MEMBER -> DELETE) - Expect DENIED..."
AUTHZ_DENY=$(curl -s -X POST http://localhost:8080/api/v1/authorize \
    -H "Content-Type: application/json" \
    -H "X-API-Key: ${RAW_API_KEY}" \
    -H "Authorization: Bearer ${JWT_TOKEN}" \
    -d "{\"subjectId\":\"${MEMBER_ID}\",\"resourceId\":\"${RESOURCE_ID}\",\"action\":\"DELETE\"}")

if echo "$AUTHZ_DENY" | grep -q '"allowed":false'; then
    echo "   Authorization DENIED verified correctly!"
else
    echo "[ERROR] Expected allowed:false but got: $AUTHZ_DENY"
    exit 1
fi

echo "7. Querying Audit Logs via GET /api/v1/audit-logs..."
AUDIT_RESP=$(curl -s -X GET "http://localhost:8080/api/v1/audit-logs?subjectId=${SUBJECT_ID}" \
    -H "X-API-Key: ${RAW_API_KEY}" \
    -H "Authorization: Bearer ${JWT_TOKEN}")

if echo "$AUDIT_RESP" | grep -q "content"; then
    echo "   Audit logs verified!"
else
    echo "[ERROR] Failed to query audit logs! Response: $AUDIT_RESP"
    exit 1
fi

echo "8. Querying OpenAPI Docs via GET /v3/api-docs..."
OPENAPI_RESP=$(curl -s -X GET http://localhost:8080/v3/api-docs)
if echo "$OPENAPI_RESP" | grep -q "openapi"; then
    echo "   OpenAPI 3.x spec verified!"
else
    echo "[ERROR] Failed to get OpenAPI spec! Response: $OPENAPI_RESP"
    exit 1
fi

echo ""
echo "================================================================="
echo "SUCCESS: All Independence Acceptance Checks Passed 100%!"
echo "================================================================="
