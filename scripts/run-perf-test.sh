#!/usr/bin/env bash
# =============================================================================
# Permissio — Multi-Tier Standalone Performance Benchmark Runner
# TRD Section 3: /authorize latency p95 < 150ms
# Supports individual request counts or multi-tier runs (100 -> 10,000 requests)
# =============================================================================

set -eo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-acme-dev-api-key-12345}"
REQUEST_COUNT=100
MODE="SINGLE"
ALL_TIERS=(100 500 1000 2000 5000 10000)

# Parse CLI arguments
for arg in "$@"; do
    case $arg in
        --smoke)
            REQUEST_COUNT=100
            MODE="SINGLE"
            shift
            ;;
        --all-tiers)
            MODE="ALL_TIERS"
            shift
            ;;
        --requests=*)
            REQUEST_COUNT="${arg#*=}"
            MODE="SINGLE"
            shift
            ;;
        --help)
            echo "Usage: ./scripts/run-perf-test.sh [--smoke] [--requests=N] [--all-tiers]"
            echo "  --requests=N   Run benchmark with N requests (e.g. 100, 500, 1000, 2000, 5000, 10000)"
            echo "  --all-tiers    Run benchmark sequentially across all 6 tiers (100 -> 10,000)"
            echo "  --smoke        Run quick smoke test with 100 requests"
            exit 0
            ;;
    esac
done

echo "================================================================="
echo "Permissio — Performance Benchmark Suite [Mode: $MODE]"
echo "Target: $BASE_URL | SLA Target (TRD Section 3): p95 < 150ms"
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
    LOGIN_RESP=$(curl -s -X POST "$BASE_URL/api/v1/auth/login" \
        -H "Content-Type: application/json" \
        -H "X-API-Key: $API_KEY" \
        -d '{"externalId":"alice.vp@acme.com","password":"Password123!"}')
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

AUTHZ_PAYLOAD="{\"subjectId\":\"$SUBJECT_ID\",\"resourceId\":\"$RESOURCE_ID\",\"action\":\"READ\"}"

# Function to execute a single benchmark run
run_benchmark_tier() {
    local COUNT=$1
    local LATENCY_FILE
    LATENCY_FILE=$(mktemp)
    local SUCCESS_COUNT=0
    local ERROR_COUNT=0
    local START_TIME
    START_TIME=$(date +%s.%N 2>/dev/null || date +%s)

    for ((i=1; i<=COUNT; i++)); do
        local RESP_DATA
        RESP_DATA=$(curl -s -w "\n%{http_code} %{time_total}" -X POST "$BASE_URL/api/v1/authorize" \
            -H "Content-Type: application/json" \
            -H "X-API-Key: $API_KEY" \
            -H "Authorization: Bearer $JWT_TOKEN" \
            -d "$AUTHZ_PAYLOAD")
        
        local HTTP_CODE
        HTTP_CODE=$(echo "$RESP_DATA" | tail -n 1 | awk '{print $1}')
        local LATENCY
        LATENCY=$(echo "$RESP_DATA" | tail -n 1 | awk '{print $2}')
        
        if [ "$HTTP_CODE" -eq 200 ]; then
            SUCCESS_COUNT=$((SUCCESS_COUNT + 1))
        else
            ERROR_COUNT=$((ERROR_COUNT + 1))
        fi

        local LATENCY_MS
        LATENCY_MS=$(awk "BEGIN {print $LATENCY * 1000}" 2>/dev/null || echo "0")
        echo "$LATENCY_MS" >> "$LATENCY_FILE"
    done

    local END_TIME
    END_TIME=$(date +%s.%N 2>/dev/null || date +%s)
    local TOTAL_TIME
    TOTAL_TIME=$(awk "BEGIN {print $END_TIME - $START_TIME}" 2>/dev/null || echo "1")
    local RPS
    RPS=$(awk "BEGIN {print $COUNT / ($TOTAL_TIME > 0 ? $TOTAL_TIME : 1)}" 2>/dev/null || echo "$COUNT")

    local SORTED_FILE
    SORTED_FILE=$(mktemp)
    sort -n "$LATENCY_FILE" > "$SORTED_FILE"

    local TOTAL
    TOTAL=$(wc -l < "$SORTED_FILE" | tr -d ' ')
    local P50_LINE=$(( (50 * TOTAL) / 100 ))
    local P90_LINE=$(( (90 * TOTAL) / 100 ))
    local P95_LINE=$(( (95 * TOTAL) / 100 ))
    local P99_LINE=$(( (99 * TOTAL) / 100 ))

    local P50
    P50=$(sed -n "${P50_LINE:-1}p" "$SORTED_FILE")
    local P90
    P90=$(sed -n "${P90_LINE:-1}p" "$SORTED_FILE")
    local P95
    P95=$(sed -n "${P95_LINE:-1}p" "$SORTED_FILE")
    local P99
    P99=$(sed -n "${P99_LINE:-1}p" "$SORTED_FILE")
    local MIN
    MIN=$(head -n 1 "$SORTED_FILE")
    local MAX
    MAX=$(tail -n 1 "$SORTED_FILE")
    local AVG
    AVG=$(awk '{sum+=$1} END {print (NR>0 ? sum/NR : 0)}' "$SORTED_FILE")

    rm -f "$LATENCY_FILE" "$SORTED_FILE"

    echo "$COUNT|$RPS|$MIN|$AVG|$P50|$P90|$P95|$P99|$MAX|$SUCCESS_COUNT|$ERROR_COUNT"
}

if [ "$MODE" = "ALL_TIERS" ]; then
    echo ""
    echo "============================================================================================="
    echo "                          MULTI-TIER SCALABILITY BENCHMARK MATRIX                            "
    echo "============================================================================================="
    printf "  %-10s %-12s %-10s %-10s %-10s %-10s %-10s %-10s\n" "Requests" "Throughput" "p50 (ms)" "p90 (ms)" "p95 (ms)" "p99 (ms)" "Max (ms)" "SLA (<150ms)"
    echo "---------------------------------------------------------------------------------------------"
    
    for TIER_COUNT in "${ALL_TIERS[@]}"; do
        RESULT=$(run_benchmark_tier "$TIER_COUNT")
        IFS='|' read -r C RPS MIN AVG P50 P90 P95 P99 MAX SUCC ERR <<< "$RESULT"
        P95_INT=${P95%.*}
        STATUS="PASSED"
        if [ "${P95_INT:-0}" -ge 150 ]; then
            STATUS="FAILED"
        fi
        printf "  %-10d %-12.2f %-10.2f %-10.2f %-10.2f %-10.2f %-10.2f %-10s\n" "$C" "$RPS" "$P50" "$P90" "$P95" "$P99" "$MAX" "$STATUS"
    done
    echo "============================================================================================="
else
    echo "Executing $REQUEST_COUNT benchmark requests against POST /api/v1/authorize..."
    RESULT=$(run_benchmark_tier "$REQUEST_COUNT")
    IFS='|' read -r C RPS MIN AVG P50 P90 P95 P99 MAX SUCC ERR <<< "$RESULT"

    echo ""
    echo "================================================================="
    echo "BENCHMARK METRICS SUMMARY [Requests: $REQUEST_COUNT]"
    echo "================================================================="
    printf "  Total Requests:       %d\n" "$C"
    printf "  Successful (200 OK):  %d\n" "$SUCC"
    printf "  Errors:               %d\n" "$ERR"
    printf "  Throughput:           %.2f req/sec\n" "$RPS"
    printf "  Min Latency:          %.2f ms\n" "$MIN"
    printf "  Avg Latency:          %.2f ms\n" "$AVG"
    printf "  p50 (Median):         %.2f ms\n" "$P50"
    printf "  p90 Latency:          %.2f ms\n" "$P90"
    printf "  p95 Latency:          %.2f ms\n" "$P95"
    printf "  p99 Latency:          %.2f ms\n" "$P99"
    printf "  Max Latency:          %.2f ms\n" "$MAX"
    echo "-----------------------------------------------------------------"

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
fi
