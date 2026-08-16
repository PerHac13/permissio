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
2. **Domain-Agnostic Primitives:** Translates any business entity (users, documents, medical records, financial assets) into **5 universal primitives**.
3. **Multi-Model Support:** Unifies Role-Based (RBAC), Attribute-Based (ABAC), and Relationship-Based (ReBAC) authorization into a single short-circuiting pipeline.
4. **Hard Multi-Tenant Isolation:** Consuming services are tenants, never code dependencies. Every piece of data is strictly isolated by `client_id`.
5. **Immutable Audit Trail:** Append-only audit logging of every decision, reason code, and policy evaluator.

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

# Run local dev server (Linux / macOS)
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
- **Migrations:** Flyway
- **Security:** Spring Security + JJWT (HMAC-SHA256) + Salted SHA-256 (API Keys) + BCrypt
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
  - [x] **125/125 tests green (100% pass rate)**
- [ ] **Epic 4 — Resource Module** *(Next Up: Tenant-scoped CRUD)*
- [ ] **Epic 5 — Relationship Module** *(ReBAC Hierarchy & Rank Ordering)*
- [ ] **Epic 6 — Authorization Engine Core** (`POST /api/v1/authorize`)
- [ ] **Epic 7 — ABAC & Business Rule Evaluators**
- [ ] **Epic 8 — Audit Logging Module**
- [ ] **Epic 9 — Observability & OpenTelemetry**

---

## 📄 License

This project is licensed under the Apache 2.0 License.
