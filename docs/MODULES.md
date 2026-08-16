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
├── common                              # 🌐 Shared Utilities & Error Envelopes
│   └── exception/
│       ├── GlobalExceptionHandler.java # @RestControllerAdvice for uniform errors
│       ├── ErrorResponse.java          # Record: { code, message, traceId }
│       ├── ConflictException.java      # HTTP 409 Conflict
│       ├── NotFoundException.java      # HTTP 404 Not Found
│       └── UnauthorizedException.java  # HTTP 401 Unauthorized
│
└── config                              # ⚙ Spring Configuration Beans
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
- **Attributes:** Contains dynamic JSON attributes (department, clearance, team) stored in the database for ABAC policy evaluation.
- **REST Endpoints (Epic 3):**
  - `POST /api/v1/subjects` — Create a subject (201 Created)
  - `GET /api/v1/subjects/{id}` — Get by internal UUID (200 OK)
  - `GET /api/v1/subjects/external/{externalId}` — Get by external ID (200 OK)
  - `GET /api/v1/subjects` — List all tenant subjects (200 OK)
  - `PUT /api/v1/subjects/{id}/attributes` — Replace ABAC attributes (200 OK)
  - `DELETE /api/v1/subjects/{id}` — Delete subject (204 No Content)
- **Tenant Isolation:** All queries scoped by `clientId` via `TenantContext`; cross-tenant lookups return 404 (never leaks existence).

### 4. `common`
- **Purpose:** Cross-cutting concerns such as standardized exception handling.
- **Uniform Error Response:**
  ```json
  {
    "code": "CONFLICT",
    "message": "Subject with externalId 'alice' already exists",
    "traceId": "N/A"
  }
  ```

---

## 🧱 Guidelines for Adding New Modules

When implementing upcoming epics (`resource`, `relationship`, `authorization`, `audit`):

1. **Package per Primitive:** Create dedicated root packages under `com.perhac.permissio.<module_name>`.
2. **Layered Structure:** Maintain standard separation:
   - `entity/`: JPA entities with `@Table(name = "...")`
   - `repository/`: Spring Data JPA repositories with explicit `clientId` parameters
   - `service/`: Business logic enforcing `TenantContext.get()`
   - `controller/`: REST endpoints with `@Valid` request DTOs
   - `dto/`: Immutable request/response records
3. **Multi-Tenancy Guardrail:** Never query a repository using only a resource ID or subject ID without the accompanying `clientId`.
