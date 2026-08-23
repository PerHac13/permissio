# Permissio — Developer Guide & Local Setup

> Practical instructions for running, testing, building, and configuring Permissio locally.

---

## Quick Commands Cheat Sheet

| Task | PowerShell / Windows | Linux / macOS / Bash | Notes |
|---|---|---|---|
| **Run Dev Server** | `.\mvnw.cmd spring-boot:run` | `./mvnw spring-boot:run` | Runs in `dev` profile with in-memory H2 |
| **Run Fast Unit Tests** | `.\mvnw.cmd test` | `./mvnw test` | Fast execution using H2 test DB |
| **Run Full Verification** | `.\mvnw.cmd verify` | `./mvnw verify` | Runs all tests + JaCoCo 80% coverage check |
| **Start Docker Stack** | `docker compose up -d` | `docker compose up -d` | Spins up Permissio + Postgres + OTel + Jaeger |
| **Seed Mock Dataset** | `.\scripts\seed-data.ps1` | `./scripts/seed-data.sh` | Populates Postgres with 3 enterprise tenants & data |
| **Run Independence Test** | `.\scripts\independence-acceptance.ps1` | `./scripts/independence-acceptance.sh` | Spins up Docker, registers tenant, validates full flow |
| **Fast Smoke Benchmark** | `bash ./scripts/run-perf-test.sh --smoke` | `./scripts/run-perf-test.sh --smoke` | Rapid 25-request latency check |
| **Full Latency Benchmark**| `bash ./scripts/run-perf-test.sh` | `./scripts/run-perf-test.sh` | Measures p50/p90/p95/p99 latency (p95 < 150ms SLA) |
| **Run k6 Smoke Test** | `k6 run perf/load-test.js -e SCENARIO=smoke` | `k6 run perf/load-test.js -e SCENARIO=smoke` | 5-second fast verification |
| **Run k6 Load Test** | `k6 run perf/load-test.js -e SCENARIO=load` | `k6 run perf/load-test.js -e SCENARIO=load` | 50 concurrent user sustained load test |

---

## Database & Environment Profiles

Permissio supports seamless switching between databases using Spring Boot profiles without any code modifications:

```
                  ┌──────────────────────┐
                  │ Spring Boot Profile  │
                  └──────────┬───────────┘
                             │
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
   [Profile: dev]      [Profile: test]     [Profile: prod]
         │                   │                   │
         ▼                   ▼                   ▼
┌──────────────────┐┌──────────────────┐┌──────────────────┐
│ H2 In-Memory DB  ││ H2 In-Memory DB  ││ PostgreSQL 16    │
│  (Persistent     ││ (Isolated Schema ││  (Production /   │
│   during run)    ││   per test suite)││   Docker Compose)│
└──────────────────┘└──────────────────┘└──────────────────┘
```

### 1. Development (`dev` Profile)
- **Database:** In-memory H2 in PostgreSQL compatibility mode (`jdbc:h2:mem:permissio;MODE=PostgreSQL`).
- **H2 Web Console:** Available at `http://localhost:8080/h2-console`
  - **JDBC URL:** `jdbc:h2:mem:permissio`
  - **Username:** `sa`
  - **Password:** *(leave blank)*
- **Health Endpoint:** `http://localhost:8080/actuator/health`

### 2. Testing (`test` Profile)
- **Database:** Isolated in-memory H2 schema (`jdbc:h2:mem:permissio_test;MODE=PostgreSQL`).
- Flyway migrations execute automatically on every test run.
- Schema is discarded immediately upon test completion.

### 3. Production (`prod` Profile)
- **Database:** Dedicated PostgreSQL 16 instance (`jdbc:postgresql://localhost:5432/permissio`).
- Reads credentials securely from [.env](.env).

---

## Environment Variables (`.env`)

Permissio uses `spring-dotenv` to load environment variables from `.env` at startup.

Copy the template to get started:
```powershell
cp .env.example .env
```

## Code Coverage & JaCoCo Gate

Permissio enforces a strict **≥ 80% line coverage threshold** across core packages.

To run the coverage check and generate the report:
```powershell
./mvnw clean verify -Dspring.profiles.active=test
```

### Viewing the HTML Report:
Open `target/site/jacoco/index.html` in any web browser to see line-by-line test execution coverage.
