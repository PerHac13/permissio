# Permissio — Architecture & Concepts

> A deep dive into Permissio's core primitives, permission hierarchy, and 4-stage authorization evaluation pipeline.

---

## The Core Philosophy: Domain Agnosticism

In traditional applications, authorization is coupled to business domains (e.g. `isTeacher()`, `canApproveExpense()`, `isDocumentOwner()`). As systems grow, this logic fragments across multiple microservices and database queries.

**Permissio decouples authorization from application domains** by reducing any system into **5 universal primitives**:

| Primitive | Definition | Real-World Examples |
|---|---|---|
| **`Subject`** | An actor or entity requesting access. | User UUID, Service Account, Employee ID, Mobile App Client |
| **`Resource`** | The target entity or asset being acted upon. | `DOCUMENT:456`, `PROJECT:10`, `REPORT:2026-Q1`, `API_ENDPOINT:/billing` |
| **`Relation`** | A hierarchical link between a Subject and a Resource. | `OWNER`, `MANAGER`, `LEAD`, `MEMBER` |
| **`Action`** | The attempted operation on the Resource. | `CREATE`, `READ`, `UPDATE`, `DELETE`, `APPROVE`, `REJECT` |
| **`Policy`** | Contextual ABAC or business rule condition. | Department match, working hours, IP whitelist, clearance level |

---

## ReBAC Permission Hierarchy

Permissio implements a strict rank-ordered relationship hierarchy:

```
┌────────────────────────────────────────────────────────────────────────┐
│ OWNER   (Rank 4)  ->  CREATE, READ, UPDATE, DELETE, APPROVE, REJECT     │
├────────────────────────────────────────────────────────────────────────┤
│ MANAGER (Rank 3)  ->  CREATE, READ, UPDATE                              │
├────────────────────────────────────────────────────────────────────────┤
│ LEAD    (Rank 2)  ->  CREATE, READ                                      │
├────────────────────────────────────────────────────────────────────────┤
│ MEMBER  (Rank 1)  ->  READ                                              │
└────────────────────────────────────────────────────────────────────────┘
```

- **Inheritance Principle:** An `OWNER` automatically inherits all capabilities of `MANAGER`, `LEAD`, and `MEMBER`.
- **Multi-Role Capability:** A Subject can hold different Relations across different Resources simultaneously (e.g. `OWNER` of `Project:A`, but only `MEMBER` of `Project:B`).

---

## The 4-Stage Authorization Pipeline

Every authorization check sent to `POST /api/v1/authorize` passes through a deterministic, fast short-circuiting pipeline:

```
               Incoming Request
                      │
                      ▼
┌──────────────────────────────────────────────────────────┐
│ 1. Multi-Tenant Identification & Context Resolution      │
│    • Validate Client API Key (Sets TenantContext)        │
│    • Validate Subject JWT Bearer Token                   │
│    • Load Subject, Resource, and Relationship Records    │
└─────────────────────────┬────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────┐
│ 2. ReBAC Evaluator (Stage 1)                             │
│    • Find Subject's highest Relation on target Resource  │
│    • Evaluate RelationHierarchy.permits(relation,action) │
└─────────────┬──────────────────────────────┬─────────────┘
              │                              │
         (Permitted)                      (Denied)
              │                              │
              ▼                              ▼
┌───────────────────────────┐   ┌──────────────────────────┐
│ 3. ABAC Evaluator (Stage 2│   │                          │
│   • Dynamic JSON attribute│   │                          │
│      matching (Dept, Team)│   │  SHORT-CIRCUIT DENIAL    │
└─────────────┬─────────────┘   │                          │
              │                 │  Return: { allowed: false│
         (Permitted)            │            reason: "..." }│
              │                 │                          │
              ▼                 │                          │
┌───────────────────────────┐   │                          │
│ 4. Business Rule Evaluator│   │                          │
│    • Time-of-day windows, │   │                          │
│      IP ranges, Quotas    │   │                          │
└─────────────┬─────────────┘   │                          │
              │                 │                          │
         (Permitted)            │                          │
              │                 │                          │
              ▼                 │                          │
┌───────────────────────────┐   │                          │
│       ALLOW DECISION      │   │                          │
│  Return: { allowed: true }│   │                          │
└─────────────┬─────────────┘   │                          │
              │                 │                          │
              └────────┬────────┘                          │
                       │                                   │
                       ▼                                   ▼
┌──────────────────────────────────────────────────────────┐
│ 5. Immutable Audit Logging                               │
│    Append-only record: (Tenant, Subject, Resource,       │
│    Action, Decision, Evaluator, Timestamp, TraceId)      │
└──────────────────────────────────────────────────────────┘
```

---

## Multi-Tenant Isolation by Design

Multi-tenancy in Permissio is **hard-isolated**:
1. **Root Tenant Anchor:** Every data table (`subjects`, `resources`, `relationships`, `policies`, `audit_logs`) includes a `client_id` foreign key.
2. **`TenantContext` ThreadLocal:** The `ApiKeyAuthenticationFilter` resolves the caller's tenant upfront and binds `TenantContext.get()` to the request thread.
3. **No Cross-Tenant Queries:** All repository lookup methods strictly require `(clientId, id)` or `(clientId, externalId)`. An entity belonging to Tenant B is completely invisible to Tenant A (returning `404 Not Found`, never leaking existence).
