# Permissio — Project Structure & Module Directory

> A comprehensive walkthrough of the Permissio codebase, its modular package design, and class responsibilities.

---

## Package Tree

```
src/main/java/com/perhac/permissio/
├── PermissioApplication.java           # Spring Boot Application Entry Point
│
├── client                              # Multi-Tenant Management (Epic 1)
│   ├── entity/Client.java              # Client JPA Entity (Tenant root)
│   ├── repository/ClientRepository.java# Tenant query interface
│   └── service/ClientService.java      # Client resolution & API key lookup
│
├── authentication                      # User Authentication & Tokens (Epic 2)
│   ├── controller/AuthController.java  # REST: POST /api/v1/auth/register, /login
│   ├── dto/AuthResponse.java           # DTO: { token, tokenType, expiresIn, subjectId, externalId }
│   ├── dto/LoginRequest.java           # DTO: { externalId, password }
│   ├── dto/RegisterRequest.java        # DTO: { externalId, password, attributes }
│   └── service/AuthService.java        # Password hashing, user verification & RS256 JWT issuing
│
├── security                            # Security Filters & Tenant Context (Epic 1 & 10)
│   ├── TenantContext.java              # ThreadLocal<UUID> tenant isolation holder
│   ├── ApiKeyHasher.java               # SHA-256 with salt API key hasher
│   ├── ApiKeyAuthenticationFilter.java # OncePerRequestFilter for X-API-Key header (never logs raw keys)
│   ├── JwtTokenProvider.java           # RS256 asymmetric JWT builder, signer, and parser
│   ├── JwtAuthenticationFilter.java    # OncePerRequestFilter for Bearer RS256 JWT token
│   └── SubjectPrincipal.java           # Authenticated principal object
│
├── subject                             # Subject Primitive (Epic 2 & 3)
│   ├── entity/Subject.java             # Subject JPA Entity with JSON attributes
│   ├── repository/SubjectRepository.java # Tenant-scoped Subject queries
│   ├── service/SubjectService.java     # Tenant-scoped CRUD & attribute management
│   ├── controller/SubjectController.java # REST: /api/v1/subjects/**
│   └── dto/
│       ├── SubjectResponse.java        # Record: { id, clientId, externalId, attributes, createdAt }
│       ├── CreateSubjectRequest.java   # DTO: { externalId, password?, attributes? }
│       └── UpdateSubjectAttributesRequest.java # DTO: { attributes }
│
├── resource                            # Resource Primitive (Epic 4)
│   ├── entity/Resource.java            # Resource JPA Entity with JSON attributes
│   ├── repository/ResourceRepository.java # Tenant-scoped Resource queries
│   ├── service/ResourceService.java    # Tenant-scoped CRUD & attribute management
│   ├── controller/ResourceController.java # REST: /api/v1/resources/**
│   └── dto/
│       ├── ResourceResponse.java       # Record: { id, clientId, resourceType, externalId, attributes, createdAt }
│       ├── CreateResourceRequest.java  # DTO: { resourceType, externalId, attributes? }
│       └── UpdateResourceAttributesRequest.java # DTO: { attributes }
│
├── relationship                        # Relationship Module / ReBAC Foundation (Epic 5)
│   ├── entity/
│   │   ├── Relation.java               # Enum with rank hierarchy (OWNER > MANAGER > LEAD > MEMBER)
│   │   └── Relationship.java           # Relationship JPA Entity (Subject -> Relation -> Resource)
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
├── authorization                       # Authorization Engine Pipeline (Epic 6 & 7)
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
├── policy                              # Policy Management & Expression Engine (Epic 7)
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
├── audit                               # Immutable Decision Audit Logging (Epic 8)
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
├── observability                       # Observability & OpenTelemetry (Epic 9)
│   ├── config/
│   │   └── OpenTelemetryConfig.java    # OTel condition beans & log export toggles
│   ├── filter/
│   │   └── TraceContextFilter.java     # MDC trace_id/span_id/clientId & X-Trace-Id header
│   ├── metrics/
│   │   └── AuthorizationMetrics.java   # Custom metrics: authz_requests_total, authz_decision_duration_seconds, authz_denials_total
│   └── tracing/
│       └── AuthorizationTracer.java    # Manual OTel spans & semantic attribute tagging
│
├── common                              # Shared Utilities & Error Envelopes
│   ├── model/
│   │   └── Action.java                 # Universal Actions: CREATE, READ, UPDATE, DELETE, APPROVE, REJECT
│   └── exception/
│       ├── GlobalExceptionHandler.java # @RestControllerAdvice for uniform errors (400, 401, 403, 404, 405, 409, 500)
│       ├── ErrorResponse.java          # Record: { code, message, traceId }
│       ├── ConflictException.java      # HTTP 409 Conflict
│       ├── ForbiddenException.java     # HTTP 403 Forbidden
│       ├── NotFoundException.java      # HTTP 404 Not Found
│       ├── UnauthorizedException.java  # HTTP 401 Unauthorized
│       └── ValidationException.java    # HTTP 400 Bad Request
│
└── config                              # Spring Configuration Beans
    ├── RsaKeyProvider.java             # RS256 RSA 2048-bit key-pair provider (auto-generates in dev/test)
    ├── PermissioProperties.java        # Centralized type-safe @ConfigurationProperties (JWT, API Keys, OTel, Logs)
    ├── SecurityConfig.java             # Stateless SecurityFilterChain setup with OpenAPI & Swagger bypass
    ├── ApiKeyHasherConfig.java         # ApiKeyHasher bean configuration
    └── PasswordEncoderConfig.java      # BCryptPasswordEncoder bean configuration
```

---

## Test Suites Structure (`src/test/java/com/perhac/permissio/`)

| Package / Suite | Scope & Responsibility |
|---|---|
| **`com.perhac.permissio.isolation`** | Living Tenant Isolation Contract Suite (TRD §9.3):<br>- `SubjectIsolationTest` — Subject list/get boundary tests<br>- `ResourceIsolationTest` — Resource query isolation<br>- `RelationshipIsolationTest` — ReBAC linkage prevention<br>- `CraftedIdAttackTest` — 404 non-existence leakage protection<br>- `IndependenceAcceptanceTest` — Standalone H2 zero-dependency acceptance |
| **`com.perhac.permissio.authorization.contract`** | `AuthorizeContractTest` — Locks `/authorize` request/response JSON shapes and DTO serialization for Phase 1 to Phase 2 compatibility. |
| **`com.perhac.permissio.security`** | - `JwtTokenProviderTest` — RS256 key signing, parsing, and HS256 algorithm confusion rejection<br>- `BeanValidationIntegrationTest` — Exhaustive validation check across all request DTOs<br>- `ApiKeyLogSanitizationTest` — Logback appender verifying raw API keys are never logged<br>- `JwtAuthenticationFilterTest`, `ApiKeyAuthenticationFilterTest` |
| **`com.perhac.permissio.config`** | - `OpenApiSmokeTest` — Verifies Swagger UI and OpenAPI 3 spec generation at `/v3/api-docs`<br>- `PermissioPropertiesTest` — Property validation across dev, test, and prod profiles |
| **Controllers & Services** | Unit & Integration tests for `client`, `authentication`, `subject`, `resource`, `relationship`, `authorization`, `policy`, `audit`, and `observability`. |

---

## Detailed Module Responsibilities

### 1. `client` (Tenant Root)
- **Purpose:** Represents the consuming client applications (tenants).
- **Core Entities:** `Client` (stores client name, hashed API key, creation timestamp).
- **Isolation Responsibility:** Every record in Permissio is owned by a `Client`.

### 2. `authentication` & `security`
- **Purpose:** Manages user authentication and request security with RS256 asymmetric signing.
- **Filter Chain Sequence:**
  1. `TraceContextFilter`: Generates or propagates `X-Trace-Id` into MDC.
  2. `ApiKeyAuthenticationFilter`: Reads `X-API-Key` -> verifies against `ClientRepository` -> populates `TenantContext.set(clientId)`.
  3. `JwtAuthenticationFilter`: Reads `Authorization: Bearer <RS256-JWT>` -> validates signature/expiry -> sets `SecurityContext` with `SubjectPrincipal`.
  4. `TenantContext.clear()`: Cleared in a `finally` block on every request to prevent ThreadLocal memory leaks.

### 3. `subject` (The Actor Primitive)
- **Purpose:** Manages actors (users, service accounts, agents) requesting permissions.
- **Attributes:** Dynamic JSON attributes (department, clearance, team) for ABAC evaluation.
- **Tenant Isolation:** All queries scoped by `clientId` via `TenantContext`; cross-tenant lookups return 404 (never leaks existence).

### 4. `resource` (The Target Primitive)
- **Purpose:** Manages target entities (documents, projects, accounts, datasets) that subjects act upon.
- **Compound Key:** Uniquely identified per tenant by `(clientId, resourceType, externalId)`.
- **Attributes:** Dynamic JSON attributes (classification, department, sensitivity) for ABAC evaluation.

### 5. `relationship` (ReBAC Foundation & Tuples)
- **Purpose:** Manages relationship tuples (`Subject -> Relation -> Resource`) powering Relationship-Based Access Control (ReBAC).
- **Hierarchy & Ranks:**
  - `OWNER (rank 4)` -> `{CREATE, READ, UPDATE, DELETE, APPROVE, REJECT}`
  - `MANAGER (rank 3)` -> `{CREATE, READ, UPDATE}`
  - `LEAD (rank 2)` -> `{CREATE, READ}`
  - `MEMBER (rank 1)` -> `{READ}`
- **Relational Integrity:** Validates that both `subjectId` and `resourceId` exist under the calling tenant before persisting.

### 6. `authorization` (Evaluation Engine Core)
- **Purpose:** Unifies ReBAC, ABAC, and Business Rule evaluation into a high-performance, short-circuiting decision engine.
- **Orchestration Pipeline:**
  1. `AuthorizationContextBuilder`: Resolves `Subject`, `Resource`, and all active `Relationship` tuples under `TenantContext.get()`. Returns 404 if entities do not exist under the tenant.
  2. `RebacEvaluator` (`@Order(1)`): Determines highest relation rank. Denies immediately if the relation cannot perform the action.
  3. `AbacEvaluator` (`@Order(2)`): Queries active tenant ABAC policies and evaluates them against subject and resource attributes using the sandboxed expression engine.
  4. `BusinessRuleEvaluator` (`@Order(3)`): Queries active tenant business rules against environmental variables.
  5. `AuditService` Integration: Every decision is recorded in `audit_logs` with evaluator name and MDC trace ID.

### 7. `policy` (Policy Management & Sandboxed SpEL)
- **Purpose:** Manages dynamic attribute matching and environment policies per tenant.
- **Security & Sandboxing:**
  - Uses `SimpleEvaluationContext.forReadOnlyDataBinding().build()`.
  - Strictly blocks Java reflection, method invocations, constructors, and class loading (`T(...)`).

### 8. `audit` (Immutable Decision Logging)
- **Purpose:** Durable, queryable record of every authorization check for compliance and security audits.
- **Trace Correlation:** Correlated with OpenTelemetry `trace_id`.

### 9. `observability` (OpenTelemetry Traces, Metrics & Logs)
- **Components:**
  - `TraceContextFilter`: Injects active trace context (`trace_id`, `span_id`, `clientId`) into SLF4J MDC and attaches `X-Trace-Id` response header.
  - `AuthorizationMetrics`: Records `authz_requests_total`, `authz_decision_duration_seconds`, and `authz_denials_total` with Prometheus compatibility.
  - `AuthorizationTracer`: Generates manual spans around `AuthorizationEngine` and each `PolicyEvaluator`.
