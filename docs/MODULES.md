# 📂 Permissio — Project Structure & Module Directory

> A comprehensive walkthrough of the Permissio codebase, its modular package design, and class responsibilities.

---

## 🌳 Package Tree

```
src/main/java/com/perhac/permissio/
├── PermissioApplication.java           # Spring Boot Application Entry Point
│
├── client                              # 🏢 Multi-Tenant Management (Epic 1)
│   ├── entity/Client.java              # Client JPA Entity (Tenant root)
│   ├── repository/ClientRepository.java# Tenant query interface
│   └── service/ClientService.java      # Client resolution & API key lookup
│
├── authentication                      # 🔐 User Authentication & Tokens (Epic 2)
│   ├── controller/AuthController.java  # REST: POST /api/v1/auth/register, /login
│   ├── dto/AuthResponse.java           # DTO: { token, tokenType, expiresIn, subjectId, externalId }
│   ├── dto/LoginRequest.java           # DTO: { externalId, password }
│   ├── dto/RegisterRequest.java        # DTO: { externalId, password, attributes }
│   └── service/AuthService.java        # Password hashing, user verification & JWT issuing
│
├── security                            # 🛡 Security Filters & Tenant Context
│   ├── TenantContext.java              # ThreadLocal<UUID> tenant isolation holder
│   ├── ApiKeyHasher.java               # SHA-256 with salt API key hasher
│   ├── ApiKeyAuthenticationFilter.java # OncePerRequestFilter for X-API-Key header
│   ├── JwtTokenProvider.java           # HMAC-SHA256 JWT builder, signer, and parser
│   ├── JwtAuthenticationFilter.java    # OncePerRequestFilter for Bearer JWT token
│   └── SubjectPrincipal.java           # Authenticated principal object
│
├── subject                             # 👤 Subject Primitive (Epic 2 + Epic 3)
│   ├── entity/Subject.java             # Subject JPA Entity with JSON attributes
│   ├── repository/SubjectRepository.java # Tenant-scoped Subject queries
│   ├── service/SubjectService.java     # Tenant-scoped CRUD & attribute management
│   ├── controller/SubjectController.java # REST: /api/v1/subjects/**
│   └── dto/
│       ├── SubjectResponse.java        # Record: { id, clientId, externalId, attributes, createdAt }
│       ├── CreateSubjectRequest.java   # DTO: { externalId, password?, attributes? }
│       └── UpdateSubjectAttributesRequest.java # DTO: { attributes }
│
├── resource                            # 📦 Resource Primitive (Epic 4)
│   ├── entity/Resource.java            # Resource JPA Entity with JSON attributes
│   ├── repository/ResourceRepository.java # Tenant-scoped Resource queries
│   ├── service/ResourceService.java    # Tenant-scoped CRUD & attribute management
│   ├── controller/ResourceController.java # REST: /api/v1/resources/**
│   └── dto/
│       ├── ResourceResponse.java       # Record: { id, clientId, resourceType, externalId, attributes, createdAt }
│       ├── CreateResourceRequest.java  # DTO: { resourceType, externalId, attributes? }
│       └── UpdateResourceAttributesRequest.java # DTO: { attributes }
│
├── relationship                        # 🔗 Relationship Module / ReBAC Foundation (Epic 5)
│   ├── entity/
│   │   ├── Relation.java               # Enum with rank hierarchy (OWNER > MANAGER > LEAD > MEMBER)
│   │   └── Relationship.java           # Relationship JPA Entity (Subject ➔ Relation ➔ Resource)
│   ├── rebac/
│   │   └── RelationHierarchy.java      # Deterministic 4x6 permission matrix & evaluation helper
│   ├── repository/
│   │   └── RelationshipRepository.java # Tenant-scoped tuple queries by Subject/Resource
│   ├── service/
│   │   └── RelationshipService.java    # Tuple CRUD, duplicate checks, relational integrity
│   ├── controller/
│   │   └── RelationshipController.java # REST: /api/v1/relationships/**
│   └── dto/
│       ├── CreateRelationshipRequest.java # DTO: { subjectId, resourceId, relation }
│       └── RelationshipResponse.java   # Record: { id, clientId, subjectId, resourceId, relation, createdAt }
│
├── authorization                       # ⚡ Authorization Engine Pipeline (Epic 6 & 7)
│   ├── controller/
│   │   └── AuthorizationController.java# REST: POST /api/v1/authorize
│   ├── engine/
│   │   └── AuthorizationEngine.java    # Evaluator pipeline orchestrator + short-circuiting
│   ├── evaluator/
│   │   ├── PolicyEvaluator.java        # Evaluator strategy interface (@Order priority)
│   │   ├── RebacEvaluator.java         # @Order(1) Highest-relation ReBAC permission matrix check
│   │   ├── AbacEvaluator.java          # @Order(2) Dynamic attribute matching policies
│   │   └── BusinessRuleEvaluator.java  # @Order(3) Time windows & environment conditions
│   ├── model/
│   │   ├── AuthorizationContext.java   # Resolved context record { subject, resource, action, relationships }
│   │   └── Decision.java               # Decision record { allowed, reason, evaluator }
│   ├── service/
│   │   └── AuthorizationContextBuilder.java # Scoped context assembler with 404 safety
│   └── dto/
│       ├── AuthorizeRequest.java       # DTO: { subjectId, resourceId, action }
│       └── AuthorizeResponse.java      # Record: { allowed, reason, evaluator }
│
├── policy                              # 📜 Policy Management & Expression Engine (Epic 7)
│   ├── controller/
│   │   └── PolicyController.java       # REST: /api/v1/policies/**
│   ├── engine/
│   │   └── PolicyEvaluationEngine.java # Sandboxed SpEL evaluator (read-only data binding, RCE-safe)
│   ├── entity/
│   │   ├── Policy.java                 # Policy JPA entity
│   │   └── PolicyType.java             # Enum: ABAC, BUSINESS_RULE
│   ├── repository/
│   │   └── PolicyRepository.java       # Tenant-scoped policy queries by type/resourceType/action
│   ├── service/
│   │   └── PolicyService.java          # Policy CRUD with Sandboxed SpEL syntax validation
│   └── dto/
│       ├── CreatePolicyRequest.java    # DTO: { resourceType, action, policyType, expression }
│       └── PolicyResponse.java         # Record: { id, clientId, resourceType, action, policyType, expression, createdAt }
│
├── audit                               # 📋 Immutable Decision Audit Logging (Epic 8)
│   ├── controller/
│   │   └── AuditController.java        # REST: GET /api/v1/audit-logs
│   ├── entity/
│   │   └── AuditLog.java               # Immutable Audit Log JPA entity with trace_id
│   ├── repository/
│   │   └── AuditLogRepository.java     # Tenant-scoped paginated and filtered queries
│   ├── service/
│   │   └── AuditService.java           # Durable audit logging with MDC traceId capture
│   └── dto/
│       └── AuditLogResponse.java       # Record: { id, clientId, subjectId, resourceId, action, allowed, reason, evaluator, traceId, evaluatedAt }
│
├── observability                       # 🔭 Observability & OpenTelemetry (Epic 9)
│   ├── config/
│   │   └── OpenTelemetryConfig.java    # OTel condition beans & log export toggles
│   ├── filter/
│   │   └── TraceContextFilter.java     # MDC trace_id/span_id/clientId & X-Trace-Id header
│   ├── metrics/
│   │   └── AuthorizationMetrics.java   # Custom metrics: authz_requests_total, authz_decision_duration_seconds, authz_denials_total
│   └── tracing/
│       └── AuthorizationTracer.java    # Manual OTel spans & semantic attribute tagging
│
├── common                              # 🌐 Shared Utilities & Error Envelopes
│   ├── model/
│   │   └── Action.java                 # Universal Actions: CREATE, READ, UPDATE, DELETE, APPROVE, REJECT
│   └── exception/
│       ├── GlobalExceptionHandler.java # @RestControllerAdvice for uniform errors
│       ├── ErrorResponse.java          # Record: { code, message, traceId }
│       ├── ConflictException.java      # HTTP 409 Conflict
│       ├── NotFoundException.java      # HTTP 404 Not Found
│       ├── UnauthorizedException.java  # HTTP 401 Unauthorized
│       └── ValidationException.java    # HTTP 400 Bad Request
│
└── config                              # ⚙ Spring Configuration Beans
    ├── PermissioProperties.java        # Centralized type-safe @ConfigurationProperties (JWT, API Keys, OTel, Logs)
    ├── SecurityConfig.java             # Stateless SecurityFilterChain setup
    ├── ApiKeyHasherConfig.java         # ApiKeyHasher bean configuration
    └── PasswordEncoderConfig.java      # BCryptPasswordEncoder bean configuration
```

---

## 📦 Detailed Module Responsibilities

### 1. `client` (Tenant Root)
- **Purpose:** Represents the consuming client applications (tenants).
- **Core Entities:** `Client` (stores client name, hashed API key, creation timestamp).
- **Isolation Responsibility:** Every record in Permissio is owned by a `Client`.

### 2. `authentication` & `security`
- **Purpose:** Manages user authentication and request security.
- **Filter Chain Sequence:**
  1. `ApiKeyAuthenticationFilter`: Reads `X-API-Key` ➔ verifies against `ClientRepository` ➔ populates `TenantContext.set(clientId)`.
  2. `JwtAuthenticationFilter`: Reads `Authorization: Bearer <jwt>` ➔ validates signature/expiry ➔ sets `SecurityContext` with `SubjectPrincipal`.
  3. `TenantContext.clear()`: Cleared in a `finally` block on every request to prevent ThreadLocal memory leaks.

### 3. `subject` (The Actor Primitive)
- **Purpose:** Manages actors (users, service accounts, agents) requesting permissions.
- **Attributes:** Dynamic JSON attributes (department, clearance, team) for ABAC evaluation.
- **REST Endpoints (Epic 3):**
  - `POST /api/v1/subjects` — Create a subject (201 Created)
  - `GET /api/v1/subjects/{id}` — Get by internal UUID (200 OK)
  - `GET /api/v1/subjects/external/{externalId}` — Get by external ID (200 OK)
  - `GET /api/v1/subjects` — List all tenant subjects (200 OK)
  - `PUT /api/v1/subjects/{id}/attributes` — Replace ABAC attributes (200 OK)
  - `DELETE /api/v1/subjects/{id}` — Delete subject (204 No Content)
- **Tenant Isolation:** All queries scoped by `clientId` via `TenantContext`; cross-tenant lookups return 404 (never leaks existence).

### 4. `resource` (The Target Primitive)
- **Purpose:** Manages target entities (documents, projects, accounts, datasets) that subjects act upon.
- **Compound Key:** Uniquely identified per tenant by `(clientId, resourceType, externalId)`.
- **Attributes:** Dynamic JSON attributes (classification, department, sensitivity) for ABAC evaluation.
- **REST Endpoints (Epic 4):**
  - `POST /api/v1/resources` — Create a resource (201 Created)
  - `GET /api/v1/resources/{id}` — Get by internal UUID (200 OK)
  - `GET /api/v1/resources/type/{resourceType}/external/{externalId}` — Get by composite type + external ID (200 OK)
  - `GET /api/v1/resources(?type={type})` — List all tenant resources with optional type filter (200 OK)
  - `PUT /api/v1/resources/{id}/attributes` — Replace ABAC attributes (200 OK)
  - `DELETE /api/v1/resources/{id}` — Delete resource (204 No Content)
- **Tenant Isolation:** Hard tenant isolation scoped by `clientId`.

### 5. `relationship` (ReBAC Foundation & Tuples)
- **Purpose:** Manages relationship tuples (`Subject ➔ Relation ➔ Resource`) powering Relationship-Based Access Control (ReBAC).
- **Hierarchy & Ranks:**
  - `OWNER (rank 4)` ➔ `{CREATE, READ, UPDATE, DELETE, APPROVE, REJECT}`
  - `MANAGER (rank 3)` ➔ `{CREATE, READ, UPDATE}`
  - `LEAD (rank 2)` ➔ `{CREATE, READ}`
  - `MEMBER (rank 1)` ➔ `{READ}`
- **Relational Integrity:** Validates that both `subjectId` and `resourceId` exist under the calling tenant before persisting.
- **Compound Uniqueness:** `(client_id, subject_id, resource_id, relation)`.
- **REST Endpoints (Epic 5):**
  - `POST /api/v1/relationships` — Create relationship tuple (201 Created)
  - `GET /api/v1/relationships/{id}` — Get by ID (200 OK)
  - `GET /api/v1/relationships(?subjectId={}&resourceId={})` — List and filter relationships (200 OK)
  - `DELETE /api/v1/relationships/{id}` — Delete relationship (204 No Content)

### 6. `authorization` (Evaluation Engine Core)
- **Purpose:** Unifies ReBAC, ABAC, and Business Rule evaluation into a high-performance, short-circuiting decision engine.
- **Orchestration Pipeline:**
  1. `AuthorizationContextBuilder`: Resolves `Subject`, `Resource`, and all active `Relationship` tuples under `TenantContext.get()`. Returns 404 if entities do not exist under the tenant.
  2. `RebacEvaluator` (`@Order(1)`): Determines highest relation rank. Denies immediately if the relation cannot perform the action.
  3. `AbacEvaluator` (`@Order(2)`): Queries active tenant ABAC policies and evaluates them against subject and resource attributes using the sandboxed expression engine.
  4. `BusinessRuleEvaluator` (`@Order(3)`): Queries active tenant business rules (e.g. time windows, business hours) against environmental variables.
  5. `AuditService` Integration: Every decision (allow or short-circuit deny) is durably recorded in `audit_logs` with evaluator name and denial reason.
- **REST Endpoints (Epic 6):**
  - `POST /api/v1/authorize` — Evaluate permission decision (200 OK: `{ "allowed": true/false, "reason": "...", "evaluator": "..." }`)

### 7. `policy` (Policy Management & Sandboxed SpEL)
- **Purpose:** Manages dynamic attribute matching and environment policies per tenant.
- **Security & Sandboxing:**
  - Uses `SimpleEvaluationContext.forReadOnlyDataBinding().build()`.
  - Strictly blocks Java reflection, method invocations, constructors, and class loading (`T(java.lang.Runtime)`).
  - Safely exposes `#subject`, `#resource`, `#action`, and `#environment` maps.
- **REST Endpoints (Epic 7):**
  - `POST /api/v1/policies` — Create tenant policy (201 Created)
  - `GET /api/v1/policies/{id}` — Get policy by ID (200 OK)
  - `GET /api/v1/policies(?type={}&resourceType={})` — List tenant policies (200 OK)
  - `DELETE /api/v1/policies/{id}` — Delete policy (204 No Content)

### 8. `audit` (Immutable Decision Logging)
- **Purpose:** Provides a durable, queryable record of every authorization check for compliance and security audits.
- **Trace Correlation:** Automatically extracts `trace_id` from SLF4J MDC or Spring Observation.
- **REST Endpoints (Epic 8):**
  - `GET /api/v1/audit-logs(?subjectId={}&resourceId={}&page={}&size={})` — Paginated and filtered decision audit log query (200 OK)

### 9. `observability` (OpenTelemetry Traces, Metrics & Logs)
- **Purpose:** Centralizes distributed tracing, custom authorization metrics, and structured log correlation.
- **Config-Driven YAML:**
  - `permissio.observability.otel.enabled` — Master switch for OpenTelemetry exporter registration.
  - `permissio.observability.otel.endpoint` — OTLP collector endpoint (`http://localhost:4318`).
  - `permissio.observability.otel.logs.enabled` — Option in config whether to emit logs to the OpenTelemetry OTLP exporter.
  - `permissio.observability.logging.console.enabled` — Ensures console log remains active.
  - `permissio.observability.logging.console.structured` — Switches between standard readable text and structured JSON.
- **Components:**
  - `TraceContextFilter`: Injects active trace context (`trace_id`, `span_id`, `clientId`) into SLF4J MDC and attaches `X-Trace-Id` response header.
  - `AuthorizationMetrics`: Records `authz_requests_total`, `authz_decision_duration_seconds`, and `authz_denials_total` with Prometheus compatibility.
  - `AuthorizationTracer`: Generates manual spans around `AuthorizationEngine` and each `PolicyEvaluator`.

### 10. `common`
- **Purpose:** Cross-cutting concerns such as standardized exception handling and domain models (`Action`).
- **Uniform Error Response:**
  ```json
  {
    "code": "CONFLICT",
    "message": "Relationship tuple already exists",
    "traceId": "N/A"
  }
  ```

---

## 🧱 Guidelines for Adding New Modules

When implementing upcoming epics (`security hardening`, `zanzibar graph`):

1. **Package per Primitive / Domain:** Create dedicated root packages under `com.perhac.permissio.<module_name>`.
2. **Layered Structure:** Maintain standard separation:
   - `entity/`: JPA entities with `@Table(name = "...")`
   - `repository/`: Spring Data JPA repositories with explicit `clientId` parameters
   - `service/`: Business logic enforcing `TenantContext.get()`
   - `controller/`: REST endpoints with `@Valid` request DTOs
   - `dto/`: Immutable request/response records
3. **Multi-Tenancy Guardrail:** Never query a repository using only a resource ID or subject ID without the accompanying `clientId`.
4. **Integration Test Teardown:** Always tear down child tables in foreign key order (`audit_logs` ➔ `policies` ➔ `relationships` ➔ `resources` ➔ `subjects` ➔ `clients`).
