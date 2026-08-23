# Permissio — Agile Engineering Backlog & Milestone Roadmap

> **Status:** All Epics Completed (Epics 0 through 12)  
> **Methodology:** Test-Driven Development (TDD) + Red-Green-Refactor + Contract Testing  
> **Code Coverage:** ≥ 80% line coverage enforced by JaCoCo Gate

---

## 1. Epic Delivery Summary

| Epic | Scope & Milestone | Status | Test Suites |
|---|---|---|---|
| **Epic 0** | Project Bootstrap, Multi-Profile DB & Flyway | COMPLETED | CI build, Flyway baseline |
| **Epic 1** | Client Module & Salted SHA-256 API Key Ingress | COMPLETED | `ApiKeyAuthenticationFilterTest`, `ApiKeyHasherTest` |
| **Epic 2** | Authentication & RS256 Asymmetric JWT Tokens | COMPLETED | `AuthControllerTest`, `JwtTokenProviderTest` |
| **Epic 3** | Subject Primitive (Tenant-Scoped CRUD) | COMPLETED | `SubjectControllerTest`, `SubjectServiceTest` |
| **Epic 4** | Resource Primitive (Compound Keys & JSON Attributes) | COMPLETED | `ResourceControllerTest`, `ResourceServiceTest` |
| **Epic 5** | Relationship ReBAC Foundation & Permission Matrix | COMPLETED | `RelationshipControllerTest`, `RelationHierarchyTest` |
| **Epic 6** | Core Authorization Engine (`POST /api/v1/authorize`) | COMPLETED | `AuthorizationControllerTest`, `AuthorizationEngineTest` |
| **Epic 7** | ABAC & Business Rule Policies (Sandboxed SpEL) | COMPLETED | `PolicyControllerTest`, `PolicyEvaluationEngineTest` |
| **Epic 8** | Immutable Decision Audit Logging | COMPLETED | `AuditControllerTest`, `AuditServiceTest` |
| **Epic 9** | Observability, Distributed Tracing & Custom Metrics | COMPLETED | `TraceContextFilterTest`, `AuthorizationMetricsTest` |
| **Epic 10** | Security Hardening (RS256, Log Sanitization, Validation) | COMPLETED | `BeanValidationIntegrationTest`, `ApiKeyLogSanitizationTest` |
| **Epic 11** | Living Tenant Isolation Contract Suite | COMPLETED | `SubjectIsolationTest`, `ResourceIsolationTest`, `CraftedIdAttackTest` |
| **Epic 12** | Documentation, Contract Stability & OpenAPI 3 UI | COMPLETED | `AuthorizeContractTest`, `OpenApiSmokeTest` |

---

## 2. Detailed Epic Breakdown

### Epic 10: Security Hardening
- **10.1 Key Provider (`RsaKeyProvider.java`):** RS256 key pair management with automatic transient 2048-bit key generation in dev/test, base64 PEM decoding in prod.
- **10.2 JWT RS256 Migration (`JwtTokenProvider.java`):** Migrated from symmetric HMAC to asymmetric RS256. Added explicit `Jwts.SIG.RS256` verification to eliminate algorithm confusion attacks.
- **10.3 DTO Bean Validation Suite (`BeanValidationIntegrationTest.java`):** Exhaustive boundary testing for all DTOs (`@NotBlank`, `@NotNull`, `@Valid`).
- **10.4 Global Exception Completeness (`GlobalExceptionHandler.java`):** Unified RFC 7807 error envelopes for 400, 401, 403, 404, 405, 409, and 500 errors.
- **10.5 API Key Log Sanitization (`ApiKeyLogSanitizationTest.java`):** Verified using Logback `ListAppender` that raw API keys are never written to log sinks.

### Epic 11: Tenant Isolation Contract Suite
- **11.1 Subject Isolation (`SubjectIsolationTest.java`):** Confirmed subjects created by Tenant A are invisible to Tenant B.
- **11.2 Resource Isolation (`ResourceIsolationTest.java`):** Confirmed resources are strictly isolated by `client_id`.
- **11.3 Relationship Isolation (`RelationshipIsolationTest.java`):** Prevented linking subjects or resources across different tenants.
- **11.4 Crafted-ID Attack Resistance (`CraftedIdAttackTest.java`):** Querying random or guessed UUIDs belonging to other tenants returns 404, ensuring zero existence leakage.
- **11.5 Independence Acceptance (`IndependenceAcceptanceTest.java`):** End-to-end acceptance flow on empty in-memory DB without external dependencies.

### Epic 12: Documentation & Contract Stability
- **12.1 OpenAPI 3 & Swagger UI:** Integrated `springdoc-openapi-starter-webmvc-ui` at `/swagger-ui.html` and `/v3/api-docs`.
- **12.2 Contract Lock (`AuthorizeContractTest.java`):** Pinned `/authorize` request and response JSON schemas to ensure zero breaking changes when migrating to Phase 2 (Zanzibar graph).
- **12.3 Complete Documentation Suite:** High-Level Design (`HLD_DESIGN.md`), Low-Level Design (`UML_LLD_DESIGN.md`), Developer Reference (`API_REFERENCE.md`), and Client Integration Guide (`CLIENT_INTEGRATION_GUIDE.md`).
