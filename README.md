# Permissio

> **A standalone, domain-agnostic authorization service** combining **RBAC**, **ABAC**, and **ReBAC** into a unified, high-performance evaluation pipeline with a future migration path to a **Zanzibar-style relationship graph**.

---

## 📋 Table of Contents

1. [Problem Statement — Why Permissio?](#-problem-statement--why-permissio)
2. [Core Concepts & Primitives](#-core-concepts--primitives)
3. [Architecture & Authorization Pipeline](#-architecture--authorization-pipeline)
4. [Tech Stack](#-tech-stack)
5. [Terminal Commands Cheat Sheet](#-terminal-commands-cheat-sheet)
6. [Database & Environment Strategy (Plug-and-Play)](#-database--environment-strategy-plug-and-play)
7. [Project Structure & Implemented Modules](#-project-structure--implemented-modules)
8. [Roadmap & Backlog Progress](#-roadmap--backlog-progress)

---

## 🎯 Problem Statement — Why Permissio?

In most applications, authorization starts with flat role checks (`USER`, `ADMIN`, `SUPER_ADMIN`) scattered throughout controllers, services, and queries. Over time, this architecture breaks down:

- **Logic Scattered Everywhere:** Role checks are copy-pasted across services, leading to inconsistent enforcement and security gaps.
- **Hierarchical & Scoped Permissions Fail:** Answering *"Can a Project Manager edit documents in only Project #10?"* or *"Can a Team Lead approve announcements during working hours?"* cannot be cleanly answered by static roles.
- **Cross-Service Coupling:** Every backend service re-invents its own auth logic, leading to duplicate code and divergent security policies.
- **Zero Audit Trail:** No centralized, immutable log of *who was granted or denied access, why, and which policy triggered the outcome*.

### The Permissio Solution:
Permissio centralizes all authorization behind a single **`/api/v1/authorize`** endpoint. It abstracts away application-specific entities (students, employees, documents, tickets) into **five generic primitives** and evaluates relationships, attributes, and business rules in a fast, short-circuiting pipeline.

**The Independence Principle:** Permissio is a tenant-isolated, self-contained service. Consuming applications are *tenants*, never dependencies. No domain-specific code or database tables ever leak into Permissio.

---

## 🧩 Core Concepts & Primitives

| Primitive | Description | Example Mapping |
|---|---|---|
| **Subject** | The actor requesting access | User ID, Service Account, Employee UUID |
| **Resource** | The target entity being acted upon | `DOCUMENT:456`, `PROJECT:10`, `EVENT:789` |
| **Relation** | Hierarchical link between Subject & Resource | `OWNER`, `MANAGER`, `LEAD`, `MEMBER` |
| **Action** | Operation attempted | `CREATE`, `READ`, `UPDATE`, `DELETE`, `APPROVE` |
| **Policy** | Attribute-based or business rule condition | Department match, working hours, IP constraints |

### Permission Hierarchy (ReBAC)
```
OWNER   (Rank 4)  →  CREATE, READ, UPDATE, DELETE, APPROVE, REJECT
MANAGER (Rank 3)  →  CREATE, READ, UPDATE
LEAD    (Rank 2)  →  CREATE, READ
MEMBER  (Rank 1)  →  READ
```

---

## 🏗 Architecture & Authorization Pipeline

```
Inbound Request
      │
      ▼
┌────────────────────────────────────────────────────────┐
│ 1. API Key Authentication (Resolves Tenant/Client)     │
└───────────────────────┬────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────┐
│ 2. JWT Validation (Resolves Subject Identity)          │
└───────────────────────┬────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────┐
│ 3. Build Authorization Context (Subject/Resource/Rels) │
└───────────────────────┬────────────────────────────────┘
                        │
        ┌───────────────┴───────────────┐
        ▼                               ▼
┌───────────────┐               ┌───────────────┐
│ ReBAC Check   │ ──(Denied)──► │  DENY (Short) │
└───────┬───────┘               └───────────────┘
        ▼ (Passed)
┌───────────────┐               ┌───────────────┐
│  ABAC Check   │ ──(Denied)──► │  DENY (Short) │
└───────┬───────┘               └───────────────┘
        ▼ (Passed)
┌───────────────┐               ┌───────────────┐
│ Business Rule │ ──(Denied)──► │  DENY (Short) │
└───────┬───────┘               └───────────────┘
        ▼ (Passed)
┌───────────────┐
│ ALLOW (Pass)  │
└───────┬───────┘
        ▼
┌────────────────────────────────────────────────────────┐
│ 4. Append-Only Audit Log (Decision, Evaluator, Reason) │
└────────────────────────────────────────────────────────┘
```

---

## 🛠 Tech Stack

- **Language:** Java 21 (LTS) — Records, pattern matching, virtual threads
- **Framework:** Spring Boot 4.1.0 (latest)
- **Persistence:** Spring Data JPA + Hibernate 7
- **Database:**
  - **Dev:** H2 in-memory (PostgreSQL compatibility mode) — *instant startup, zero external dependencies*
  - **Test:** H2 in-memory isolated schema
  - **Prod:** PostgreSQL 16 (via Docker Compose or standalone instance)
- **Migrations:** Flyway
- **Security:** Spring Security + JJWT 0.13.0 + BCrypt + Salted SHA-256 (API keys)
- **Testing:** JUnit 5, Mockito, AssertJ, JaCoCo (strictly enforcing ≥ 80% line coverage)
- **CI/CD:** GitHub Actions (JDK 21, Maven verify, automated coverage reports)

---

## 🚀 Terminal Commands Cheat Sheet

### 🟢 1. Run the Application (Dev Mode — H2 In-Memory)

```powershell
# Windows PowerShell / CMD
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```
- **Base URL:** `http://localhost:8080`
- **H2 Web Console:** `http://localhost:8080/h2-console`
  - **JDBC URL:** `jdbc:h2:mem:permissio`
  - **Username:** `sa`
  - **Password:** *(leave blank)*
- **Health Probe:** `http://localhost:8080/actuator/health`

---

### 🧪 2. Run TDD Test Suite & Code Coverage

```powershell
# Run all 37 Unit & Integration tests (fast in-memory H2)
.\mvnw.cmd test

# Run tests + JaCoCo coverage validation (enforces >= 80% line coverage)
.\mvnw.cmd verify
```
> 📊 **JaCoCo Coverage Report:** Open `target/site/jacoco/index.html` in your browser.

---

### 🐳 3. Run with PostgreSQL (Production Mode)

```powershell
# 1. Start PostgreSQL 16 container via Docker Compose
docker compose up -d

# 2. Run application with prod profile
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=prod
```

---

### 📦 4. Build Executable Production JAR

```powershell
# Build fat JAR (skipping test phase for speed)
.\mvnw.cmd clean package -DskipTests

# Run the generated artifact
java -jar target/permissio-0.0.1-SNAPSHOT.jar
```

---

## 🗄 Database & Environment Strategy (Plug-and-Play)

Permissio utilizes Spring profiles so switching between development (H2) and production (PostgreSQL) requires **zero code changes**:

| Profile | Target Database | Use Case | Key Configuration |
|---|---|---|---|
| **`dev`** | **H2 (In-Memory)** | Local dev without Docker | `jdbc:h2:mem:permissio;MODE=PostgreSQL`, H2 Console enabled |
| **`test`** | **H2 (In-Memory)** | Automated testing | `jdbc:h2:mem:permissio_test;MODE=PostgreSQL`, Clean DB per test run |
| **`prod`** | **PostgreSQL 16** | Production / Staging | `jdbc:postgresql://localhost:5432/permissio`, reads credentials from `.env` |

### Environment Variables ([.env](.env))

Placeholder Template: [.env.example](.env.example)

---

## 📂 Project Structure & Implemented Modules

```
src/main/java/com/perhac/permissio/
├── PermissioApplication.java           # Main Spring Boot Runner
│
├── client                              # Epic 1: Tenant / Client Module
│   ├── entity/Client.java              # Client JPA Entity (Tenant root)
│   ├── repository/ClientRepository.java# Repository with findByApiKeyHash
│   └── service/ClientService.java      # Client resolution & registration
│
├── common                              # Shared Utilities & Exceptions
│   └── exception/
│       ├── GlobalExceptionHandler.java # Uniform ErrorResponse mapping
│       ├── ErrorResponse.java          # Record: { code, message, traceId }
│       ├── NotFoundException.java      # 404 Exception
│       └── UnauthorizedException.java  # 401 Exception
│
├── config                              # Spring Configuration Beans
│   ├── SecurityConfig.java             # Stateless filter chain configuration
│   └── ApiKeyHasherConfig.java         # Salted ApiKeyHasher bean
│
└── security                            # Security & Multi-Tenancy Context
    ├── TenantContext.java              # ThreadLocal<UUID> tenant holder
    ├── ApiKeyHasher.java               # SHA-256 with salt key hasher
    └── ApiKeyAuthenticationFilter.java # OncePerRequestFilter for X-API-Key
```

---

## 📊 Roadmap & Backlog Progress

- [x] **Epic 0 — Project Bootstrap & Foundation**
  - [x] Spring Boot 4.1.0 + Java 21 setup
  - [x] Plug-and-play H2 (Dev/Test) & PostgreSQL (Prod) profile wiring
  - [x] Flyway migrations baseline (`V1__init_clients_table.sql`)
  - [x] GitHub Actions CI pipeline with JaCoCo coverage gate (≥ 80%)
  - [x] Docker Compose configuration for PostgreSQL 16
- [x] **Epic 1 — Tenant & Client Module (`client` package)**
  - [x] `Client` entity and `ClientRepository`
  - [x] `TenantContext` ThreadLocal tenant scope management
  - [x] Salted `ApiKeyHasher` (keys never stored in plaintext)
  - [x] `ApiKeyAuthenticationFilter` with automatic tenant scoping and leak-proof cleanup
  - [x] Standard `GlobalExceptionHandler` with machine-readable error envelopes
  - [x] **100% test pass rate (37/37 tests green)**
- [ ] **Epic 2 — Authentication & JWT Module (`authentication`, `security`)** *(Working)*
  - [ ] Subject registration & login endpoints (`/api/v1/auth/register`, `/api/v1/auth/login`)
  - [ ] `JwtTokenProvider` & `JwtAuthenticationFilter`
- [ ] **Epic 3 — Subject Module** (Tenant-scoped CRUD)
- [ ] **Epic 4 — Resource Module** (Tenant-scoped CRUD)
- [ ] **Epic 5 — Relationship Module** (ReBAC Hierarchy)
- [ ] **Epic 6 — Authorization Engine Core** (`POST /api/v1/authorize`)
- [ ] **Epic 7 — ABAC & Business Rule Evaluators**
- [ ] **Epic 8 — Audit Logging Module**
- [ ] **Epic 9 — Observability (OpenTelemetry Traces & Metrics)**
