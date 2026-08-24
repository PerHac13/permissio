# Permissio — High-Level Design (HLD) & System Architecture

> **System-Level Architecture, Subsystem Topologies, and Component Interactions**  
> Features interactive **Mermaid.js** diagrams with direct deep-links to [Low-Level Design (LLD)](UML_LLD_DESIGN.md) specifications.

---

## 1. Master High-Level Architecture (Interactive Ecosystem)

The following diagram illustrates how external consuming services interact with Permissio, how requests flow through core subsystems, and how data and telemetry are isolated.

> Click on any component box below to jump directly into its **Low-Level Design (LLD)** specification.

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#3b82f6', 'primaryTextColor': '#1e1e1e', 'lineColor': '#6b7280', 'textColor': '#1e1e1e'}}}%%
flowchart TB
    subgraph Clients["Consuming Client Ecosystem (Tenants)"]
        ClientApp1["Microservice A<br/>(FinTech Service)"]
        ClientApp2["Microservice B<br/>(Healthcare Service)"]
        ClientApp3["Microservice C<br/>(E-Commerce App)"]
    end

    subgraph Ingress["Ingress & Security Boundary"]
        SecurityFilter["1. Dual-Layer Auth & Multi-Tenant Ingress<br/>(X-API-Key + RS256 Bearer JWT)"]
        KeyProvider["2. Asymmetric Key Provider<br/>(RS256 Private/Public Key Management)"]
    end

    subgraph CoreEngine["Permissio Core Decision Engine"]
        ContextBuilder["3. Scoped Context Builder<br/>(TenantContext Resolution & 404 Non-Leakage)"]
        AuthzPipeline["4. 4-Stage Decision Pipeline<br/>(ReBAC -> ABAC -> Business Rules)"]
        SpelEngine["5. Sandboxed SpEL Engine<br/>(RCE-Safe Read-Only Data Binding)"]
    end

    subgraph DataTier["Persistence & Storage Tier"]
        DB[(PostgreSQL 16 Multi-Tenant Store<br/>Hard Tenant-Scoped Tables)]
    end

    subgraph TelemetryTier["Telemetry & Audit Subsystem"]
        AuditLogger["6. Immutable Decision Auditor<br/>(Append-Only Audit Logs)"]
        OTelExporter["7. Observability & Tracing<br/>(OpenTelemetry + Prometheus + Jaeger)"]
    end

    %% Ingress Connections
    ClientApp1 -->|POST /authorize<br/>Headers: Key, JWT| SecurityFilter
    ClientApp2 -->|POST /authorize<br/>Headers: Key, JWT| SecurityFilter
    ClientApp3 -->|POST /authorize<br/>Headers: Key, JWT| SecurityFilter

    SecurityFilter --> KeyProvider
    SecurityFilter --> ContextBuilder
    ContextBuilder --> AuthzPipeline

    %% Pipeline Connections
    AuthzPipeline --> SpelEngine
    ContextBuilder -->|Query Tenant Tuples| DB
    SpelEngine -->|Load Tenant Policies| DB

    %% Audit & Telemetry
    AuthzPipeline -->|Async / Durable Log| AuditLogger
    AuthzPipeline -->|Spans & Custom Metrics| OTelExporter
    AuditLogger -->|Write Audit Record| DB

    %% Interactive Click Links into LLD
    click SecurityFilter "UML_LLD_DESIGN.md#2-spring-security--multi-tenant-filter-chain" "Go to LLD: Filter Chain Sequence"
    click KeyProvider "UML_LLD_DESIGN.md#5-rs256-asymmetric-key-management--lifecycle" "Go to LLD: Key Management Lifecycle"
    click ContextBuilder "UML_LLD_DESIGN.md#4-post-apiv1authorize-evaluation-pipeline" "Go to LLD: Context Builder & Non-Existence Leakage"
    click AuthzPipeline "UML_LLD_DESIGN.md#3-authorization-engine-uml-class-diagram" "Go to LLD: Class Diagram & Pipeline"
    click SpelEngine "UML_LLD_DESIGN.md#6-sandboxed-spel-expression-evaluation-architecture" "Go to LLD: Sandboxed SpEL Engine"
    click DB "UML_LLD_DESIGN.md#1-multi-tenant-entity-relationship-diagram-erd" "Go to LLD: Entity Relationship Diagram"
    click AuditLogger "UML_LLD_DESIGN.md#1-multi-tenant-entity-relationship-diagram-erd" "Go to LLD: Audit Log Schema"
    click OTelExporter "UML_LLD_DESIGN.md#2-spring-security--multi-tenant-filter-chain" "Go to LLD: Trace Context & MDC Correlation"

    classDef ingressStyle fill:#93c5fd,stroke:#1e40af,stroke-width:2px,color:#1e1e1e;
    classDef engineStyle fill:#86efac,stroke:#166534,stroke-width:2px,color:#1e1e1e;
    classDef dataStyle fill:#fde68a,stroke:#92400e,stroke-width:2px,color:#1e1e1e;
    classDef teleStyle fill:#fca5a5,stroke:#991b1b,stroke-width:2px,color:#1e1e1e;

    class SecurityFilter,KeyProvider ingressStyle;
    class ContextBuilder,AuthzPipeline,SpelEngine engineStyle;
    class DB dataStyle;
    class AuditLogger,OTelExporter teleStyle;
```

---

## 2. Subsystem Breakdown & Architecture Deep Dive

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#93c5fd', 'primaryTextColor': '#1e1e1e', 'lineColor': '#6b7280', 'textColor': '#1e1e1e'}}}%%
graph TD
    subgraph S1["1. INGRESS & IDENTITY SUBSYSTEM"]
        A1[Client Request] --> A2[TraceContextFilter]
        A2 --> A3[ApiKeyAuthenticationFilter]
        A3 --> A4[TenantContext.set clientId]
        A4 --> A5[JwtAuthenticationFilter]
        A5 --> A6[Validate RS256 Signature]
    end

    subgraph S2["2. DOMAIN-AGNOSTIC ENTITY MODELING"]
        B1[Universal Primitives]
        B1 --> B2[Subject: Actors]
        B1 --> B3[Resource: Target Assets]
        B1 --> B4[Relation: Hierarchical Links]
        B1 --> B5[Action: CRUD / Custom Verbs]
    end

    subgraph S3["3. HYBRID DECISION ORCHESTRATION"]
        C1[Stage 1: ReBAC Matrix] -->|Rank >= Action| C2[Stage 2: Dynamic ABAC]
        C2 -->|SpEL Attribute Match| C3[Stage 3: Business Rules]
        C3 -->|Time / Env Check| C4[Final ALLOW Decision]
        C1 -.->|Insufficient Rank| CD1[SHORT-CIRCUIT DENY]
        C2 -.->|Attribute Mismatch| CD2[SHORT-CIRCUIT DENY]
        C3 -.->|Rule Violation| CD3[SHORT-CIRCUIT DENY]
    end

    subgraph S4["4. AUDIT & TELEMETRY SUBSYSTEM"]
        D1[AuditService] --> D2[Write Immutable Audit Row]
        D3[AuthorizationTracer] --> D4[Inject Trace / Span Context]
        D5[AuthorizationMetrics] --> D6[Export Prometheus & Micrometer Counters]
    end

    S1 --> S2
    S2 --> S3
    S3 --> S4
```

---

## 3. Multi-Tenant Hard Isolation Model

In Permissio, tenants are cryptographically and relationally isolated:

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#93c5fd', 'primaryTextColor': '#1e1e1e', 'lineColor': '#6b7280', 'textColor': '#1e1e1e'}}}%%
flowchart LR
    subgraph RequestContext["Request Scoping"]
        HTTP[Incoming Request] --> Filter[ApiKey Filter]
        Filter --> Context[ThreadLocal TenantContext]
    end

    subgraph Partitioning["Database Multi-Tenancy (Logical Separation)"]
        Context --> Query["WHERE client_id = :clientId"]
        Query --> TenantA[Tenant A Data Slice]
        Query --> TenantB[Tenant B Data Slice]
        Query --> TenantC[Tenant C Data Slice]
    end

    subgraph NonLeakage["404 Non-Existence Guardrail"]
        TenantA -.->|Attacker attempts guessing UUID from Tenant B| Guard[404 NOT_FOUND<br/>Zero Existence Leaks]
    end

    classDef tenant fill:#67e8f9,stroke:#0e7490,stroke-width:2px,color:#1e1e1e;
    class TenantA,TenantB,TenantC tenant;
```

---

## 4. Zanzibar Migration Roadmap (Phase 1 to Phase 2)

Permissio was architected specifically to allow a seamless zero-downtime migration from relational ReBAC (Phase 1) to a full Zanzibar-style distributed relationship graph (Phase 2):

```mermaid
%%{init: {'theme': 'base', 'themeVariables': {'primaryColor': '#93c5fd', 'primaryTextColor': '#1e1e1e', 'lineColor': '#6b7280', 'textColor': '#1e1e1e'}}}%%
flowchart TD
    subgraph Phase1["Phase 1: Present (Layered Monolith + Relational Tuples)"]
        P1Engine[Authorization Engine] --> P1Store[(PostgreSQL 16<br/>Relationships Table)]
        P1Engine --> P1Evaluator[Deterministic RelationHierarchy Matrix]
    end

    subgraph ContractLock["Locked API Contract (POST /api/v1/authorize)"]
        Lock[AuthorizeContractTest<br/>Guarantees zero breaking changes to client payload & response shape]
    end

    subgraph Phase2["Phase 2: Zanzibar Graph Engine (Future Migration)"]
        P2Engine[Zanzibar Graph Engine] --> P2Graph[(Distributed Graph Store / SpiceDB)]
        P2Engine --> P2Traverse[Recursive Graph Expansion & Check API]
    end

    Phase1 --> ContractLock
    ContractLock --> Phase2
```

---

## 5. Interactive Navigation Index

| Subsystem | High-Level Description | Detailed Low-Level Design (LLD) Link |
|---|---|---|
| **Multi-Tenant Schema** | Relational data schema, keys & constraints | -> [View Entity Relationship Diagram (ERD)](UML_LLD_DESIGN.md#1-multi-tenant-entity-relationship-diagram-erd) |
| **Security Filter Chain** | API key hashing & RS256 JWT validation | -> [View Filter Chain Sequence Diagram](UML_LLD_DESIGN.md#2-spring-security--multi-tenant-filter-chain) |
| **Engine Class Diagram** | Evaluator plugin hierarchy & engine design | -> [View Authorization Engine Class Diagram](UML_LLD_DESIGN.md#3-authorization-engine-uml-class-diagram) |
| **Decision Pipeline** | Step-by-step evaluation & short-circuiting | -> [View Pipeline Decision Tree](UML_LLD_DESIGN.md#4-post-apiv1authorize-evaluation-pipeline) |
| **Key Lifecycle** | RS256 key management & confusion defense | -> [View Key Lifecycle Flowchart](UML_LLD_DESIGN.md#5-rs256-asymmetric-key-management--lifecycle) |
| **Sandboxed SpEL** | RCE-safe expression evaluation context | -> [View SpEL Sandbox Architecture](UML_LLD_DESIGN.md#6-sandboxed-spel-expression-evaluation-architecture) |
