#!/usr/bin/env bash
# =============================================================================
# Permissio — Standalone Performance & Smoke Test Benchmark Runner
# TRD Section 3: /authorize latency p95 < 150ms
# =============================================================================

set -eo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-acme-dev-api-key-12345}"
REQUEST_COUNT=100
MODE="LOAD"

# Parse CLI arguments
for arg in "$@"; do
    case $arg in
        --smoke)
            REQUEST_COUNT=25
            MODE="SMOKE"
            shift
            ;;
        --requests=*)
            REQUEST_COUNT="${arg#*=}"
            shift
            ;;
        --help)
            echo "Usage: ./scripts/run-perf-test.sh [--smoke] [--requests=N]"
            exit 0
            ;;
    esac
done

echo "================================================================="
echo "Permissio — Performance & Latency Benchmark [Mode: $MODE]"
echo "Target: $BASE_URL | Requests: $REQUEST_COUNT"
echo "SLA Contract (TRD Section 3): p95 latency < 150ms"
echo "================================================================="

# 1. Setup Benchmark Credentials
echo "Resolving benchmark credentials..."
REGISTER_RESP=$(curl -s -X POST "$BASE_URL/api/v1/auth/register" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $API_KEY" \
    -d "{\"externalId\":\"bench_usr_$(date +%s)\",\"password\":\"BenchPass123!\"}" 2>/dev/null || true)

JWT_TOKEN=$(echo "$REGISTER_RESP" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
SUBJECT_ID=$(echo "$REGISTER_RESP" | grep -o '"subjectId":"[^"]*' | cut -d'"' -f4)

if [ -z "$JWT_TOKEN" ]; then
    # Fallback to pre-seeded user if available
    LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -H "X-API-Key: $API_KEY" \
        -d '{"externalId":"alice@acme.com","password":"password123"}')
    JWT_TOKEN=$(echo "$LOGIN_RESP" | grep -o '"token":"[^"]*' | cut -d'"' -f4)
    SUBJECT_ID=$(echo "$LOGIN_RESP" | grep -o '"subjectId":"[^"]*' | cut -d'"' -f4)
fi

RESOURCE_RESP=$(curl -s -X POST "$BASE_URL/api/v1/resources" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $API_KEY" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -d "{\"resourceType\":\"DOCUMENT\",\"externalId\":\"bench_res_$(date +%s)\"}" 2>/dev/null || true)

RESOURCE_ID=$(echo "$RESOURCE_RESP" | grep -o '"id":"[^"]*' | cut -d'"' -f4)
if [ -z "$RESOURCE_ID" ]; then
    RESOURCE_ID="11111111-0002-0000-0000-000000000001"
fi

curl -s -X POST "$BASE_URL/api/v1/relationships" \
    -H "Content-Type: application/json" \
    -H "X-API-Key: $API_KEY" \
    -H "Authorization: Bearer $JWT_TOKEN" \
    -d "{\"subjectId\":\"$SUBJECT_ID\",\"resourceId\":\"$RESOURCE_ID\",\"relation\":\"OWNER\"}" >/dev/null 2>&1 || true

echo "Ready. Subject: $SUBJECT_ID, Resource: $RESOURCE_ID"
echo "Executing $REQUEST_COUNT benchmark requests against POST /api/v1/authorize..."

AUTHZ_PAYLOAD="{\"subjectId\":\"$SUBJECT_ID\",\"resourceId\":\"$RESOURCE_ID\",\"action\":\"READ\"}"
LATENCY_FILE=$(mktemp)
START_BENCHMARK_TIME=$(date +%s.%N 2>/dev/null || date +%s)
SUCCESS_COUNT=0
ERROR_COUNT=0

# Execute requests measuring total time in ms
for ((i=1; i<=REQUEST_COUNT; i++)); do
    RESP_DATA=$(curl -s -w "\n%{http_code} %{time_total}" -X POST "$BASE_URL/api/v1/authorize" \
        -H "Content-Type: application/json" \
        -H "X-API-Key: $API_KEY" \
        -H "Authorization: Bearer $JWT_TOKEN" \
        -d "$AUTHZ_PAYLOAD")
    
    HTTP_CODE=$(echo "$RESP_DATA" | tail -n 1 | awk '{print $1}')
    LATENCY=$(echo "$RESP_DATA" | tail -n 1 | awk '{print $2}')
    
    if [ "$HTTP_CODE" -eq 200 ]; then
        SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
    else
        ERROR_COUNT=$((ERROR_COUNT + 1))
    fi

    # Convert seconds to ms
    LATENCY_MS=$(awk "BEGIN {print $LATENCY * 1000}" 2>/dev/null || echo "0")
    echo "$LATENCY_MS" >> "$LATENCY_FILE"
done

END_BENCHMARK_TIME=$(date +%s.%N 2>/dev/null || date +%s)
TOTAL_TIME=$(awk "BEGIN {print $END_BENCHMARK_TIME - $START_BENCHMARK_TIME}" 2>/dev/null || echo "1")
RPS=$(awk "BEGIN {print $REQUEST_COUNT / ($TOTAL_TIME > 0 ? $TOTAL_TIME : 1)}" 2>/dev/null || echo "$REQUEST_COUNT")

# Calculate Percentiles
SORTED_FILE=$(mktemp)
sort -n "$LATENCY_FILE" > "$SORTED_FILE"

TOTAL=$(wc -l < "$SORTED_FILE" | tr -d ' ')
P50_LINE=$(( (50 * TOTAL) / 100 ))
P90_LINE=$(( (90 * TOTAL) / 100 ))
P95_LINE=$(( (95 * TOTAL) / 100 ))
P99_LINE=$(( (99 * TOTAL) / 100 ))

P50=$(sed -n "${P50_LINE:-1}p" "$SORTED_FILE")
P90=$(sed -n "${P90_LINE:-1}p" "$SORTED_FILE")
P95=$(sed -n "${P95_LINE:-1}p" "$SORTED_FILE")
P99=$(sed -n "${P99_LINE:-1}p" "$SORTED_FILE")
MIN=$(head -n 1 "$SORTED_FILE")
MAX=$(tail -n 1 "$SORTED_FILE")
AVG=$(awk '{sum+=$1} END {print (NR>0 ? sum/NR : 0)}' "$SORTED_FILE")

rm -f "$LATENCY_FILE" "$SORTED_FILE"

echo ""
echo "================================================================="
echo "BENCHMARK METRICS SUMMARY [Mode: $MODE]"
echo "================================================================="
printf "  Total Requests:       %d\n" "$TOTAL"
printf "  Successful (200 OK):  %d\n" "$SUCCESS_COUNT"
printf "  Errors:               %d\n" "$ERROR_COUNT"
printf "  Throughput:           %.2f req/sec\n" "$RPS"
printf "  Min Latency:          %.2f ms\n" "$MIN"
printf "  Avg Latency:          %.2f ms\n" "$AVG"
printf "  p50 (Median):         %.2f ms\n" "$P50"
printf "  p90 Latency:          %.2f ms\n" "$P90"
printf "  p95 Latency:          %.2f ms\n" "$P95"
printf "  p99 Latency:          %.2f ms\n" "$P99"
printf "  Max Latency:          %.2f ms\n" "$MAX"
echo "-----------------------------------------------------------------"

# SLA Evaluation (p95 < 150ms)
P95_INT=${P95%.*}
if [ "${P95_INT:-0}" -lt 150 ]; then
    echo "PASSED: p95 latency (${P95} ms) meets TRD Section 3 SLA target (< 150 ms)."
    echo "================================================================="
    exit 0
else
    echo "FAILED: p95 latency (${P95} ms) exceeded 150 ms SLA target!"
    echo "================================================================="
    exit 1
fi
