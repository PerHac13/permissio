# Permissio — Internal Developer API Reference & Technical Specification

> **Audience:** Internal Backend & Systems Engineers  
> **Status:** Production / Feature Complete (Phase 1 MVP)  
> **Base URL:** `http://<host>:<port>/api/v1`  
> **Interactive Documentation:** `http://<host>:<port>/swagger-ui.html` | OpenAPI Spec: `/v3/api-docs`

---

## 1. Authentication & Security Model

Permissio implements a dual-layer security architecture enforcing strict multi-tenancy and cryptographic identity isolation on every HTTP request.

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                                INCOMING HTTP REQUEST                                   │
├──────────────────────────────────────────┬─────────────────────────────────────────────┤
│ Header: X-API-Key: <salted-hash-key>     │ Identifies calling Service/Tenant (Client)  │
├──────────────────────────────────────────┼─────────────────────────────────────────────┤
│ Header: Authorization: Bearer <jwt>      │ Identifies End-User / Subject identity      │
│                                          │ (Signed with RS256 Asymmetric RSA Key Pair) │
└──────────────────────────────────────────┴─────────────────────────────────────────────┘
```

### 1.1 Security Headers Summary
| Header | Required | Purpose | Applied Endpoints |
|---|---|---|---|
| `X-API-Key` | **Yes** | Resolves tenant ID into `TenantContext` | All `/api/v1/**` endpoints (including `/auth/**`) |
| `Authorization` | **Yes** | `Bearer <RS256-JWT>` authenticates Subject | All `/api/v1/**` except `/auth/register` & `/auth/login` |
| `Content-Type` | **Yes** | Must be `application/json` | All `POST` / `PUT` requests |
| `X-Trace-Id` | Optional | Client-injected trace ID for distributed tracing (OTel MDC correlated) | Returned on all responses |

### 1.2 Unauthenticated Public Endpoints
The following endpoints bypass both `X-API-Key` and JWT filters:
- `/actuator/health`, `/actuator/info`, `/actuator/metrics`, `/actuator/prometheus`
- `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`
- `/h2-console/**` (Dev profile only)

---

## 2. Standard Error Response Envelope

All error responses across all controllers, filter validations, and handlers strictly return the RFC 7807-inspired `ErrorResponse` envelope:

```json
{
  "code": "VALIDATION_ERROR",
  "message": "subjectId is required; action is required",
  "traceId": "c3d98f7e21a4b56c"
}
```

### 2.1 Standard Error Codes
| HTTP Status | Code String | Description |
|---|---|---|
| `400 Bad Request` | `VALIDATION_ERROR` | Request payload failed Bean Validation (`@NotNull`, `@NotBlank`, etc.) |
| `400 Bad Request` | `MALFORMED_REQUEST` | Request payload is not parseable JSON |
| `400 Bad Request` | `TYPE_MISMATCH` | Path parameter or query param type mismatch (e.g. invalid UUID) |
| `400 Bad Request` | `MISSING_HEADER` | Missing required HTTP header |
| `401 Unauthorized` | `UNAUTHORIZED` | Invalid/missing API Key or invalid/expired/tampered/wrong-tenant JWT |
| `403 Forbidden` | `FORBIDDEN` | Authenticated Subject does not hold requisite permissions or access denied |
| `404 Not Found` | `NOT_FOUND` | Entity does not exist within the caller's tenant scope (never leaks cross-tenant existence) |
| `405 Method Not Allowed` | `METHOD_NOT_ALLOWED` | HTTP verb not supported for this route |
| `409 Conflict` | `CONFLICT` | Unique constraint violation (e.g. duplicate external ID within tenant) |
| `500 Internal Error` | `INTERNAL_ERROR` | Unhandled server exception (logged with stack trace and traceId) |

---

## 3. Core Authorization Endpoint

### `POST /api/v1/authorize`
Evaluates whether a Subject is permitted to perform an Action on a Resource.

- **Security:** Requires `X-API-Key` + `Authorization: Bearer <token>`
- **Evaluation Order:** ReBAC (Hierarchical Rank) ➔ ABAC (SpEL dynamic attributes) ➔ Business Rules (Time/Environment)
- **Short-circuiting:** First failing evaluator halts execution immediately and logs denial.

#### Request Body (`application/json`)
```json
{
  "subjectId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "resourceId": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
  "action": "UPDATE"
}
```

| Field | Type | Validation | Description |
|---|---|---|---|
| `subjectId` | UUID | `@NotNull` | Permissio Subject primary key |
| `resourceId` | UUID | `@NotNull` | Permissio Resource primary key |
| `action` | Enum | `@NotNull` | One of `CREATE`, `READ`, `UPDATE`, `DELETE`, `APPROVE`, `REJECT` |

#### Response: 200 OK — Allowed
```json
{
  "allowed": true,
  "reason": null,
  "evaluator": "RebacEvaluator"
}
```

#### Response: 200 OK — Denied
```json
{
  "allowed": false,
  "reason": "NO_QUALIFYING_RELATIONSHIP",
  "evaluator": "RebacEvaluator"
}
```

#### Denial Reason Codes
| Reason Code | Evaluator | Meaning |
|---|---|---|
| `NO_QUALIFYING_RELATIONSHIP` | `RebacEvaluator` | Subject has no relation or relation rank is insufficient for action |
| `ABAC_POLICY_VIOLATION` | `AbacEvaluator` | Subject/Resource attribute expression evaluated to false |
| `BUSINESS_RULE_VIOLATION` | `BusinessRuleEvaluator` | Time window, IP whitelist, or environmental rule failed |

---

## 4. Authentication Endpoints (`/api/v1/auth`)

### `POST /api/v1/auth/register`
Provisions a new Subject under the calling client tenant and returns an RS256-signed JWT.

- **Headers:** `X-API-Key: <client-key>` (No JWT required)

#### Request Body
```json
{
  "externalId": "usr_99182",
  "password": "SecurePassword123!",
  "attributes": {
    "department": "Engineering",
    "clearanceLevel": 3
  }
}
```

#### Response: 201 Created
```json
{
  "token": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiI2YmE3YjgxMC05ZGFkLTExZDEtODBiNC0wMGMwNGZkNDMwYzgiLCJjbGllbnRJZCI6IjExMTExMTExLTExMTEtMTExMS0xMTExLTExMTExMTExMTExMSIsImV4dGVybmFsSWQiOiJ1c3JfOTkxODIiLCJpYXQiOjE3MDgwMDAwMDAsImV4cCI6MTcwODAwMDkwMH0.xxx",
  "tokenType": "Bearer",
  "expiresIn": 900000,
  "subjectId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "externalId": "usr_99182"
}
```

---

### `POST /api/v1/auth/login`
Authenticates an existing Subject using password credentials and issues a fresh RS256-signed JWT.

#### Request Body
```json
{
  "externalId": "usr_99182",
  "password": "SecurePassword123!"
}
```

#### Response: 200 OK
Returns standard `AuthResponse` with JWT token.

---

## 5. Subject Management Endpoints (`/api/v1/subjects`)

All Subject endpoints operate strictly within the tenant resolved from `X-API-Key`.

| Method | Path | Description | Success Status |
|---|---|---|---|
| `POST` | `/api/v1/subjects` | Create/provision a Subject | `201 Created` |
| `GET` | `/api/v1/subjects` | List all Subjects in tenant | `200 OK` |
| `GET` | `/api/v1/subjects/{id}` | Get Subject by internal UUID | `200 OK` (or `404`) |
| `GET` | `/api/v1/subjects/by-external-id/{externalId}` | Get Subject by external string ID | `200 OK` (or `404`) |
| `PUT` | `/api/v1/subjects/{id}/attributes` | Replace/update JSON attributes | `200 OK` |
| `DELETE` | `/api/v1/subjects/{id}` | Delete Subject & cascade relationships | `204 No Content` |

#### Subject Schema (`SubjectResponse`)
```json
{
  "id": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "clientId": "11111111-1111-1111-1111-111111111111",
  "externalId": "usr_99182",
  "attributes": {
    "department": "Engineering",
    "role": "Tech Lead"
  },
  "createdAt": "2026-08-23T10:15:30Z"
}
```

---

## 6. Resource Management Endpoints (`/api/v1/resources`)

Manages domain-agnostic resource assets scoped to the tenant.

| Method | Path | Description | Success Status |
|---|---|---|---|
| `POST` | `/api/v1/resources` | Register a new Resource | `201 Created` |
| `GET` | `/api/v1/resources` | List all Resources (optional `?resourceType=`) | `200 OK` |
| `GET` | `/api/v1/resources/{id}` | Get Resource by internal UUID | `200 OK` (or `404`) |
| `GET` | `/api/v1/resources/by-external-id` | Query `?resourceType=&externalId=` | `200 OK` (or `404`) |
| `PUT` | `/api/v1/resources/{id}/attributes` | Update dynamic attributes | `200 OK` |
| `DELETE` | `/api/v1/resources/{id}` | Delete Resource | `204 No Content` |

#### Create Resource Request (`CreateResourceRequest`)
```json
{
  "resourceType": "DOCUMENT",
  "externalId": "doc_fin_2026_q3",
  "attributes": {
    "confidentiality": "HIGH",
    "ownerTeam": "FINANCE"
  }
}
```

---

## 7. Relationship Management Endpoints (`/api/v1/relationships`)

Defines ReBAC relationship tuples linking `(Subject, Resource, Relation)` within a tenant.

| Method | Path | Description | Success Status |
|---|---|---|---|
| `POST` | `/api/v1/relationships` | Create relationship tuple | `201 Created` |
| `GET` | `/api/v1/relationships` | Filter relationships by `?subjectId=` or `?resourceId=` | `200 OK` |
| `GET` | `/api/v1/relationships/{id}` | Get single relationship tuple | `200 OK` |
| `DELETE` | `/api/v1/relationships/{id}` | Remove relationship tuple | `204 No Content` |

#### Supported Relation Enums & Capabilities
| Relation | Rank | Permitted Actions |
|---|---|---|
| `OWNER` | 4 | `CREATE`, `READ`, `UPDATE`, `DELETE`, `APPROVE`, `REJECT` |
| `MANAGER` | 3 | `CREATE`, `READ`, `UPDATE` |
| `LEAD` | 2 | `CREATE`, `READ` |
| `MEMBER` | 1 | `READ` |

---

## 8. Policy Management Endpoints (`/api/v1/policies`)

Manages dynamic ABAC and Business Rule evaluation policies using sandboxed, RCE-safe Spring Expression Language (SpEL).

| Method | Path | Description | Success Status |
|---|---|---|---|
| `POST` | `/api/v1/policies` | Define a SpEL attribute/rule policy | `201 Created` |
| `GET` | `/api/v1/policies` | List policies (optional `?resourceType=&action=`) | `200 OK` |
| `GET` | `/api/v1/policies/{id}` | Get policy by UUID | `200 OK` |
| `DELETE` | `/api/v1/policies/{id}` | Delete policy | `204 No Content` |

#### Policy Expression Example
```json
{
  "resourceType": "DOCUMENT",
  "action": "UPDATE",
  "expression": "#subject.attributes['department'] == #resource.attributes['ownerTeam']"
}
```

---

## 9. Audit Log Endpoints (`/api/v1/audit-logs`)

Immutable append-only audit trail querying. Every decision evaluated at `/authorize` generates exactly one audit row correlated with OTel `traceId`.

| Method | Path | Query Parameters | Description |
|---|---|---|---|
| `GET` | `/api/v1/audit-logs` | `page`, `size`, `subjectId`, `resourceId`, `allowed`, `from`, `to` | Paginated audit search |

#### Audit Log Response Schema
```json
{
  "id": "7ca8c921-1ebe-22e2-91c5-11d15fe541d9",
  "clientId": "11111111-1111-1111-1111-111111111111",
  "subjectId": "6ba7b810-9dad-11d1-80b4-00c04fd430c8",
  "resourceId": "6ba7b811-9dad-11d1-80b4-00c04fd430c8",
  "action": "UPDATE",
  "allowed": true,
  "reason": null,
  "evaluator": "RebacEvaluator",
  "traceId": "c3d98f7e21a4b56c",
  "createdAt": "2026-08-23T10:15:31.104Z"
}
```

---

## 10. Developer Integration & Testing Verification

### 10.1 Running Test Suites
```bash
# Run all unit and integration tests
./mvnw clean test

# Run the dedicated Tenant Isolation Suite (Epic 11)
./mvnw test -Dtest="*IsolationTest,CraftedIdAttackTest,IndependenceAcceptanceTest"

# Run Security & Contract Verification Suites (Epics 10 & 12)
./mvnw test -Dtest="JwtTokenProviderTest,BeanValidationIntegrationTest,GlobalExceptionHandlerTest,ApiKeyLogSanitizationTest,OpenApiSmokeTest,AuthorizeContractTest"

# Full verification with JaCoCo Coverage Gate (>= 80%)
./mvnw clean verify -Dspring.profiles.active=test
```

### 10.2 OpenTelemetry & Trace Correlation
To observe real-time distributed traces and metrics:
1. Run local OTel Collector / Jaeger:
   ```bash
   docker compose up -d
   ```
2. Run Permissio:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Check Prometheus metrics: `http://localhost:8080/actuator/prometheus` (filters: `authz_requests_total`, `authz_decision_duration_seconds_max`, `authz_denials_total`).
