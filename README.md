# 🛡 Permissio

> **A standalone, domain-agnostic authorization service** combining **RBAC**, **ABAC**, and **ReBAC** into a unified, high-performance evaluation pipeline with a future migration path to a **Zanzibar-style relationship graph**.

[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Coverage](https://img.shields.io/badge/JaCoCo-≥80%25-blue.svg)](target/site/jacoco/index.html)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

---

## 🧭 Documentation Directory

To keep documentation clean and maintainable as the codebase expands, detailed technical documentation has been modularized:

| Document | Description |
|---|---|
| **[🏗 Architecture & Authorization Pipeline](docs/ARCHITECTURE.md)** | Core primitives (`Subject`, `Resource`, `Relation`, `Action`, `Policy`), ReBAC hierarchy, and 4-stage decision pipeline. |
| **[🛠 Development & Local Setup Guide](docs/DEVELOPMENT.md)** | Terminal commands cheat sheet, H2 vs PostgreSQL profile configuration, `.env` setup, and JaCoCo coverage guide. |
| **[📂 Project Structure & Module Directory](docs/MODULES.md)** | Codebase package structure, class responsibilities, filter chain flow, and conventions for new modules. |
| **[📦 Dependencies & Build Reference Guide](.reference/Permissio-Dependencies-Guide.md)** | Detailed breakdown of every starter, library, and plugin declared in `pom.xml`. |
| **[📋 Agile & TDD Backlog](.reference/Permissio-Backlog.md)** | Comprehensive roadmap, sprint grouping, and progress across all epics (0 through 12). |

---

## 🎯 The Core Problem & Solution

In most architectures, authorization is fragmented: hardcoded role checks (`hasRole('ADMIN')`) are scattered throughout controllers, services, and SQL queries. This makes hierarchical relationships (*"Can a Project Lead edit documents in Project #10?"*) or contextual conditions (*"Can a Manager approve expenses during working hours?"*) difficult to maintain and verify.

### How Permissio Solves This:
1. **Centralized Decision Engine:** Exposes a single high-performance **`POST /api/v1/authorize`** endpoint.
2. **Domain-Agnostic Primitives:** Translates any business entity (users, documents, medical records, financial assets) into universal primitives (`Subject`, `Resource`, `Relation`, `Action`, `Policy`).
3. **Multi-Model Support:** Unifies Role-Based (RBAC), Attribute-Based (ABAC with Sandboxed SpEL), and Relationship-Based (ReBAC) authorization into a single short-circuiting pipeline.
4. **Hard Multi-Tenant Isolation:** Consuming services are tenants, never code dependencies. Every piece of data is strictly isolated by `client_id`.
5. **Immutable Audit Trail:** Append-only audit logging of every decision, reason code, evaluator, and trace ID correlation via **`GET /api/v1/audit-logs`**.

---

## ⚡ Quickstart in 60 Seconds

### 1. Clone & Run (In-Memory Dev Mode)
Permissio boots out of the box with zero external dependencies using an embedded H2 database:

```bash
# Clone the repository
git clone https://github.com/PerHac13/permissio.git
cd permissio

# Run local dev server (Windows)
.\mvnw.cmd spring-boot:run

# Run local dev server (Linux / macOS / Git Bash)
./mvnw spring-boot:run
```

- **Base URL:** `http://localhost:8080`
- **H2 Web Console:** `http://localhost:8080/h2-console` (JDBC URL: `jdbc:h2:mem:permissio`, User: `sa`, Password: *blank*)
- **Health Check:** `http://localhost:8080/actuator/health`

### 2. Run the Test Suite & Coverage Gate
```bash
# Run all unit & integration tests + JaCoCo coverage check (>= 80% required)
./mvnw clean verify -Dspring.profiles.active=test
```

---

## 🛠 Tech Stack

- **Language:** Java 21 (LTS) — Records, Pattern Matching, Sealed Types
- **Framework:** Spring Boot 4.1.0 (latest)
- **Persistence:** Spring Data JPA + Hibernate 7
- **Databases:**
  - **Dev / Test:** H2 In-Memory (PostgreSQL compatibility mode)
  - **Production:** PostgreSQL 16 (via Docker Compose or Cloud RDS)
- **Migrations:** Flyway (`V1` through `V6`)
- **Security:** Spring Security + JJWT (HMAC-SHA256) + Salted SHA-256 (API Keys) + BCrypt
- **Policy Engine:** Sandboxed Spring Expression Language (SpEL) with `SimpleEvaluationContext` (RCE-safe)
- **JSON Engine:** Jackson 3 (`tools.jackson.core:jackson-databind`)
- **Testing:** JUnit 5, Mockito, AssertJ, Spring Test, JaCoCo (≥ 80% line coverage enforcement)

---

## 📊 Roadmap & Milestone Progress

- [x] **Epic 0 — Project Bootstrap & Infrastructure**
  - [x] Spring Boot 4.1.0 + Java 21 setup
  - [x] Multi-profile configuration (H2 dev/test, PostgreSQL prod)
  - [x] Flyway migrations baseline (`V1__init_clients_table.sql`)
  - [x] CI pipeline with JaCoCo coverage enforcement
- [x] **Epic 1 — Multi-Tenant & Client Module (`client`)**
  - [x] `Client` entity and `ClientRepository`
  - [x] `TenantContext` ThreadLocal tenant scope management
  - [x] Salted `ApiKeyHasher` (keys never stored in plaintext)
  - [x] `ApiKeyAuthenticationFilter` with automatic tenant scoping
- [x] **Epic 2 — Authentication & JWT Module (`authentication`, `security`)**
  - [x] `Subject` JPA entity and Flyway migration (`V2__init_subjects_table.sql`)
  - [x] BCrypt password hashing & HMAC-SHA256 `JwtTokenProvider`
  - [x] `JwtAuthenticationFilter` with Bearer token validation
  - [x] Registration & login endpoints (`/api/v1/auth/register`, `/api/v1/auth/login`)
- [x] **Epic 3 — Subject Module (Tenant-Scoped CRUD & Attributes)**
  - [x] `SubjectService` — tenant-scoped create, get, list, update attributes, delete
  - [x] `SubjectController` — 6 REST endpoints under `/api/v1/subjects`
  - [x] DTOs: `SubjectResponse`, `CreateSubjectRequest`, `UpdateSubjectAttributesRequest`
  - [x] Cross-tenant isolation: subjects invisible across tenants (returns 404, never leaks existence)
- [x] **Epic 4 — Resource Module (Tenant-Scoped CRUD & Attribute Management)**
  - [x] `Resource` JPA entity and Flyway migration (`V3__init_resources_table.sql`)
  - [x] Compound uniqueness on `(client_id, resource_type, external_id)`
  - [x] `ResourceService` — tenant-scoped create, get, list/filter by type, update attributes, delete
  - [x] `ResourceController` — 6 REST endpoints under `/api/v1/resources`
  - [x] Dynamic Jackson JSON attributes serialization/deserialization
  - [x] Hard cross-tenant isolation (returns 404, never leaks existence across boundaries)
- [x] **Epic 5 — Relationship Module (ReBAC Foundation: Roles, Hierarchy & Tenant-Scoped Tuples)**
  - [x] `Relation` enum with explicit rank ordering (`OWNER > MANAGER > LEAD > MEMBER`)
  - [x] Universal `Action` enum (`CREATE, READ, UPDATE, DELETE, APPROVE, REJECT`)
  - [x] Deterministic `RelationHierarchy` 4×6 permission matrix
  - [x] `Relationship` JPA entity and Flyway migration (`V4__init_relationships_table.sql`)
  - [x] Compound uniqueness on `(client_id, subject_id, resource_id, relation)`
  - [x] `RelationshipService` — tenant-scoped create, get by ID, filter by subject/resource, delete
  - [x] `RelationshipController` — REST endpoints under `/api/v1/relationships`
  - [x] Hard cross-tenant relational integrity (cannot link subjects/resources from other tenants)
- [x] **Epic 6 — Authorization Engine Core (`POST /api/v1/authorize`)**
  - [x] `Decision` and `AuthorizationContext` domain records
  - [x] `PolicyEvaluator` plugin interface with `@Order` chain execution
  - [x] `RebacEvaluator` (priority 1) resolving highest relationship rank and matrix permissions
  - [x] `AuthorizationContextBuilder` scoped to `TenantContext` with 404 non-existence safety
  - [x] `AuthorizationEngine` orchestrator with fast short-circuiting on denial
  - [x] `AuthorizationController` (`POST /api/v1/authorize`)
- [x] **Epic 7 — ABAC & Business Rule Evaluators**
  - [x] Flyway migration `V5__init_policies_table.sql` for tenant policies
  - [x] Sandboxed SpEL engine (`PolicyEvaluationEngine`) using `SimpleEvaluationContext.forReadOnlyDataBinding()` (RCE-safe)
  - [x] `Policy` JPA entity and `PolicyRepository`
  - [x] `AbacEvaluator` (priority 2) matching subject/resource attributes
  - [x] `BusinessRuleEvaluator` (priority 3) evaluating environmental conditions and time windows
  - [x] Policy CRUD endpoints under `/api/v1/policies`
- [x] **Epic 8 — Audit Logging Module**
  - [x] Flyway migration `V6__init_audit_logs_table.sql` with `trace_id` column
  - [x] `AuditLog` JPA entity and `AuditLogRepository`
  - [x] `AuditService` capturing every decision (allow/deny), evaluator name, reason code, and MDC trace ID
  - [x] Audit query API (`GET /api/v1/audit-logs`) with pagination and filtering
- [ ] **Epic 9 — Observability & OpenTelemetry** *(Next Up: Spans, Metrics, MDC trace correlation)*
- [ ] **Epic 10 — Security Hardening & RS256 JWT**
- [ ] **Epic 11 — Tenant Isolation Contract Suite**
- [ ] **Epic 12 — Documentation & Contract Stability**

---

## 📄 License

This project is licensed under the Apache 2.0 License.
