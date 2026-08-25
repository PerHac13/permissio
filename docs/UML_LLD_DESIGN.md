# Permissio — UML Class Diagrams & Low-Level Design (LLD)

> **Visual Architectural Specification & Subsystem Modeling**  
> Rendered using standard GitHub-compatible **Mermaid.js** diagrams.  
> **System Overview:** For the master system-level view, see **[High-Level Design (HLD)](HLD_DESIGN.md)**.

---

## 1. Multi-Tenant Entity Relationship Diagram (ERD)

This diagram models the database schema, foreign key relationships, compound unique constraints, and tenant anchoring across PostgreSQL tables.

```mermaid
erDiagram
    CLIENTS ||--o{ SUBJECTS : "owns (1:N)"
    CLIENTS ||--o{ RESOURCES : "owns (1:N)"
    CLIENTS ||--o{ RELATIONSHIPS : "owns (1:N)"
    CLIENTS ||--o{ POLICIES : "owns (1:N)"
    CLIENTS ||--o{ AUDIT_LOGS : "owns (1:N)"

    SUBJECTS ||--o{ RELATIONSHIPS : "has (1:N)"
    RESOURCES ||--o{ RELATIONSHIPS : "has (1:N)"

    CLIENTS {
        UUID id PK "gen_random_uuid()"
        VARCHAR name "Tenant Display Name"
        VARCHAR api_key_hash "SHA-256(rawKey + salt)"
        TIMESTAMP created_at "now()"
    }

    SUBJECTS {
        UUID id PK "gen_random_uuid()"
        UUID client_id FK "REFERENCES clients(id)"
        VARCHAR external_id "UK(client_id, external_id)"
        VARCHAR password_hash "BCrypt hash"
        JSONB attributes "{dept, role, clearance}"
        TIMESTAMP created_at "now()"
    }

    RESOURCES {
        UUID id PK "gen_random_uuid()"
        UUID client_id FK "REFERENCES clients(id)"
        VARCHAR resource_type "DOCUMENT, PROJECT, etc."
        VARCHAR external_id "UK(client_id, resource_type, external_id)"
        JSONB attributes "{ownerTeam, classification}"
        TIMESTAMP created_at "now()"
    }

    RELATIONSHIPS {
        UUID id PK "gen_random_uuid()"
        UUID client_id FK "REFERENCES clients(id)"
        UUID subject_id FK "REFERENCES subjects(id)"
        UUID resource_id FK "REFERENCES resources(id)"
        VARCHAR relation "OWNER, MANAGER, LEAD, MEMBER"
        TIMESTAMP created_at "now()"
    }

    POLICIES {
        UUID id PK "gen_random_uuid()"
        UUID client_id FK "REFERENCES clients(id)"
        VARCHAR resource_type "Target entity type"
        VARCHAR action "CREATE, READ, UPDATE, DELETE..."
        VARCHAR policy_type "ABAC, BUSINESS_RULE"
        TEXT expression "Sandboxed SpEL expression"
    }

    AUDIT_LOGS {
        UUID id PK "gen_random_uuid()"
        UUID client_id FK "REFERENCES clients(id)"
        UUID subject_id "Subject UUID"
        UUID resource_id "Resource UUID"
        VARCHAR action "Attempted action"
        BOOLEAN allowed "true / false"
        VARCHAR reason "Denial reason code"
        VARCHAR evaluator "Evaluator that decided"
        VARCHAR trace_id "OTel trace identifier"
        TIMESTAMP created_at "now()"
    }
```

---

## 2. Spring Security & Multi-Tenant Filter Chain

Sequence of request interception, MDC correlation, tenant context resolution, and RS256 JWT validation.

```mermaid
sequenceDiagram
    autonumber
    actor Client as Consuming Client Service
    participant TraceFilter as TraceContextFilter
    participant ApiKeyFilter as ApiKeyAuthenticationFilter
    participant ClientService as ClientService
    participant TenantCtx as TenantContext (ThreadLocal)
    participant JwtFilter as JwtAuthenticationFilter
    participant JwtProvider as JwtTokenProvider (RS256)
    participant SecCtx as SecurityContextHolder
    participant Controller as REST Controller

    Client->>TraceFilter: HTTP Request (Headers: X-API-Key, Authorization, X-Trace-Id)
    activate TraceFilter
    TraceFilter->>TraceFilter: Extract or generate trace_id
    TraceFilter->>TraceFilter: Bind trace_id & span_id to SLF4J MDC
    
    TraceFilter->>ApiKeyFilter: doFilterInternal()
    activate ApiKeyFilter
    ApiKeyFilter->>ClientService: resolveByApiKey(rawKey)
    activate ClientService
    ClientService-->>ApiKeyFilter: Return Client(id = tenantUuid)
    deactivate ClientService
    
    ApiKeyFilter->>TenantCtx: set(tenantUuid)
    ApiKeyFilter->>JwtFilter: doFilterInternal()
    activate JwtFilter
    
    opt Authorization Header present
        JwtFilter->>JwtProvider: validateToken(jwt)
        activate JwtProvider
        JwtProvider->>JwtProvider: Verify RS256 signature with PublicKey
        JwtProvider-->>JwtFilter: Valid (true)
        deactivate JwtProvider
        
        JwtFilter->>JwtProvider: getClientIdFromToken(jwt)
        JwtFilter->>JwtFilter: Assert tokenClientId == TenantContext.get()
        JwtFilter->>SecCtx: setAuthentication(SubjectPrincipal)
    end

    JwtFilter->>Controller: Dispatch to Endpoint
    activate Controller
    Controller-->>JwtFilter: ResponseEntity(200 OK / 201 Created)
    deactivate Controller

    JwtFilter-->>ApiKeyFilter: Return response
    deactivate JwtFilter
    
    ApiKeyFilter->>TenantCtx: clear() [finally block]
    deactivate ApiKeyFilter

    TraceFilter-->>Client: Response (Header: X-Trace-Id: abc123xyz)
    deactivate TraceFilter
```

---

## 3. Authorization Engine UML Class Diagram

The object-oriented design of the authorization engine, evaluator plugin chain, and domain models.

```mermaid
classDiagram
    class PolicyEvaluator {
        <<interface>>
        +evaluate(AuthorizationContext context) Decision
        +supports(AuthorizationContext context) boolean
        +getOrder() int
    }

    class RebacEvaluator {
        +evaluate(AuthorizationContext context) Decision
        +supports(AuthorizationContext context) boolean
        +getOrder() int : 1
    }

    class AbacEvaluator {
        -PolicyRepository policyRepository
        -PolicyEvaluationEngine evaluationEngine
        +evaluate(AuthorizationContext context) Decision
        +supports(AuthorizationContext context) boolean
        +getOrder() int : 2
    }

    class BusinessRuleEvaluator {
        -PolicyRepository policyRepository
        -PolicyEvaluationEngine evaluationEngine
        +evaluate(AuthorizationContext context) Decision
        +supports(AuthorizationContext context) boolean
        +getOrder() int : 3
    }

    class AuthorizationEngine {
        -List~PolicyEvaluator~ evaluators
        -AuthorizationTracer tracer
        -AuthorizationMetrics metrics
        +authorize(AuthorizationContext context) Decision
    }

    class AuthorizationContext {
        <<record>>
        +Subject subject
        +Resource resource
        +Action action
        +List~Relationship~ relationships
        +Map~String, Object~ environment
    }

    class Decision {
        <<record>>
        +boolean allowed
        +String reason
        +String evaluator
        +allow(String evaluator)$ Decision
        +deny(String reason, String evaluator)$ Decision
    }

    class RelationHierarchy {
        <<utility>>
        -Map~Relation, Set~Action~~ PERMISSION_MATRIX$
        +permits(Relation relation, Action action)$ boolean
    }

    class Relation {
        <<enumeration>>
        OWNER (rank: 4)
        MANAGER (rank: 3)
        LEAD (rank: 2)
        MEMBER (rank: 1)
        +int rank()
    }

    class Action {
        <<enumeration>>
        CREATE
        READ
        UPDATE
        DELETE
        APPROVE
        REJECT
    }

    PolicyEvaluator <|.. RebacEvaluator : implements
    PolicyEvaluator <|.. AbacEvaluator : implements
    PolicyEvaluator <|.. BusinessRuleEvaluator : implements
    AuthorizationEngine o-- PolicyEvaluator : executes chain
    AuthorizationEngine ..> AuthorizationContext : consumes
    AuthorizationEngine ..> Decision : returns
    RebacEvaluator ..> RelationHierarchy : queries
    RelationHierarchy ..> Relation : evaluates
    RelationHierarchy ..> Action : evaluates
```

---

## 4. POST /api/v1/authorize Evaluation Pipeline

Step-by-step decision workflow with short-circuiting logic and audit integration.

```mermaid
flowchart TD
    Start([Incoming Request: POST /api/v1/authorize]) --> Step1[1. Resolve Context in TenantContext]
    Step1 --> CheckEntities{Subject & Resource<br/>exist in Tenant?}
    
    CheckEntities -- No --> Ret404[Return 404 NOT_FOUND<br/>No existence leakage]
    CheckEntities -- Yes --> Step2[2. ReBAC Evaluator: Priority 1]
    
    Step2 --> RebacCheck{Has Relationship &<br/>Relation permits Action?}
    RebacCheck -- No / Insufficient --> Deny1[Decision: DENIED<br/>Reason: NO_QUALIFYING_RELATIONSHIP<br/>Evaluator: RebacEvaluator]
    
    RebacCheck -- Yes --> Step3[3. ABAC Evaluator: Priority 2]
    Step3 --> HasAbacPolicy{Active ABAC<br/>Policies for Resource?}
    
    HasAbacPolicy -- No --> Step4[4. Business Rule Evaluator: Priority 3]
    HasAbacPolicy -- Yes --> SpelEval1{SpEL Expression<br/>evaluates to TRUE?}
    SpelEval1 -- No --> Deny2[Decision: DENIED<br/>Reason: ABAC_POLICY_VIOLATION<br/>Evaluator: AbacEvaluator]
    
    SpelEval1 -- Yes --> Step4
    Step4 --> HasRulePolicy{Active Business<br/>Rules for Resource?}
    
    HasRulePolicy -- No --> Allow[Decision: ALLOWED<br/>Reason: null<br/>Evaluator: Last Evaluator]
    HasRulePolicy -- Yes --> SpelEval2{Rule Expression<br/>evaluates to TRUE?}
    SpelEval2 -- No --> Deny3[Decision: DENIED<br/>Reason: BUSINESS_RULE_VIOLATION<br/>Evaluator: BusinessRuleEvaluator]
    SpelEval2 -- Yes --> Allow

    Deny1 --> Audit[5. Write Immutable Audit Row<br/>Capture Tenant, Action, Decision, Reason, TraceId]
    Deny2 --> Audit
    Deny3 --> Audit
    Allow --> Audit

    Audit --> Metrics[6. Increment Metrics & Spans<br/>authz_requests_total, authz_decision_duration_seconds]
    Metrics --> Response([Return JSON: 200 OK<br/>allowed: true / false])

    classDef allowStyle fill:#d4edda,stroke:#28a745,stroke-width:2px;
    classDef denyStyle fill:#f8d7da,stroke:#dc3545,stroke-width:2px;
    classDef stepStyle fill:#e2e3e5,stroke:#383d41,stroke-width:1px;

    class Allow allowStyle;
    class Deny1,Deny2,Deny3 denyStyle;
    class Step1,Step2,Step3,Step4,Audit,Metrics stepStyle;
```

---

## 5. RS256 Asymmetric Key Management & Lifecycle

Visualizing key configuration resolution across development, testing, and production.

```mermaid
flowchart LR
    Start([Application Startup]) --> CheckProfile{Spring Profile?}
    
    CheckProfile -- dev or test --> CheckConfigDev{Keys in application.yaml?}
    CheckConfigDev -- Provided --> LoadConfig[Decode Base64 PEM Keys]
    CheckConfigDev -- Not Provided --> AutoGen[RsaKeyProvider Auto-Generates<br/>Transient 2048-bit RSA KeyPair]
    
    CheckProfile -- prod --> CheckConfigProd{PERMISSIO_JWT_PRIVATE_KEY &<br/>PERMISSIO_JWT_PUBLIC_KEY set?}
    CheckConfigProd -- Missing --> FatalError[Fail Fast Startup:<br/>IllegalStateException]
    CheckConfigProd -- Valid PEM --> LoadConfig
    
    AutoGen --> Ready[RSA KeyPair Loaded]
    LoadConfig --> Ready
    
    Ready --> Sign[Private Key -> Signs Outgoing JWTs via RS256]
    Ready --> Verify[Public Key -> Verifies Incoming Bearer Tokens]
    Verify --> ConfuseBlock[Explicit Jwts.SIG.RS256 Requirement<br/>Blocks HS256 Algorithm Confusion Attacks]

    classDef success fill:#d4edda,stroke:#28a745,stroke-width:2px;
    classDef fail fill:#f8d7da,stroke:#dc3545,stroke-width:2px;
    class Ready,Sign,Verify,ConfuseBlock success;
    class FatalError fail;
```

---

## 6. Sandboxed SpEL Expression Evaluation Architecture

How Permissio isolates policy evaluation to prevent Remote Code Execution (RCE).

```mermaid
flowchart TD
    subgraph Sandboxed Context
        Context[SimpleEvaluationContext<br/>forReadOnlyDataBinding]
        MapSub[#subject: Map of attributes]
        MapRes[#resource: Map of attributes]
        MapAct[#action: String]
        MapEnv[#environment: Map of time, ip, env]
    end

    Policy[Stored Policy String:<br/>#subject.attributes['department'] == #resource.attributes['ownerTeam']] --> Parser[SpEL ExpressionParser]
    
    Parser --> EvalEngine[PolicyEvaluationEngine]
    Context --> EvalEngine
    MapSub --> Context
    MapRes --> Context
    MapAct --> Context
    MapEnv --> Context

    EvalEngine --> Exec{Evaluate}
    Exec -->|Safe Property Access| Result[Boolean: true / false]
    
    subgraph Blocked Malicious Invocations
        Att1[T java.lang.Runtime] -.->|BLOCKED| Error[EvaluationException]
        Att2[new java.io.File...] -.->|BLOCKED| Error
        Att3[System.exit] -.->|BLOCKED| Error
    end

    classDef safe fill:#d1ecf1,stroke:#0c5460,stroke-width:1px;
    classDef block fill:#f8d7da,stroke:#721c24,stroke-width:1px;
    class Context,MapSub,MapRes,MapAct,MapEnv,Result safe;
    class Att1,Att2,Att3,Error block;
```
