# Permissio — Client Integration & Developer Setup Guide

> **For:** Engineers integrating consuming microservices, web apps, or backend services with Permissio.  
> **Protocol:** REST JSON / HTTP  
> **Interactive Sandbox (Swagger UI):** `http://<permissio-host>:8080/swagger-ui.html`

---

## Architecture at a Glance

Permissio operates as a standalone, domain-agnostic decision service. Consuming services **never implement internal role-checking logic**; instead, they delegate authorization decisions to Permissio via a single HTTP call:

```
┌─────────────────────────┐                     ┌─────────────────────────┐
│     Client Service      │                     │   Permissio Engine      │
│  (e.g., Document API)   │                     │                         │
└───────────┬─────────────┘                     └───────────┬─────────────┘
            │                                               │
            │ 1. POST /api/v1/authorize                     │
            │    { subjectId, resourceId, action: "READ" }  │
            │    Headers: X-API-Key, Bearer <JWT>           │
            ├──────────────────────────────────────────────►│
            │                                               │ 2. Evaluate ReBAC/ABAC
            │ 3. { allowed: true }                          │    & Log to Audit Trail
            │◄──────────────────────────────────────────────┤
            │                                               │
            ▼                                               ▼
     (Proceed / 403)
```

---

## Quickstart: Local Environment Setup

### 1. Run Permissio locally with Docker Compose

```bash
# Clone the Permissio repository
git clone https://github.com/PerHac13/permissio.git
cd permissio

# Spin up Permissio + PostgreSQL + OTel Collector + Jaeger
docker compose up -d

# Verify healthy startup
curl http://localhost:8080/actuator/health
# Response: {"status":"UP"}
```

- **Permissio API:** `http://localhost:8080`
- **Swagger UI:** `http://localhost:8080/swagger-ui.html`
- **Jaeger Tracing UI:** `http://localhost:16686`

---

## 5-Step Integration Workflow

### Step 1: Obtain Client API Key
Each consuming application acts as a tenant client. Securely configure your service with:
- `PERMISSIO_BASE_URL`: `http://localhost:8080/api/v1`
- `PERMISSIO_API_KEY`: Your tenant API key string (e.g. `your-client-api-key`)

All HTTP requests to Permissio must include the header:
```http
X-API-Key: your-client-api-key
```

---

### Step 2: Provision Users & Obtain JWT Tokens
When a user registers or logs into your application, obtain an RS256-signed JWT token from Permissio:

#### Register User (`POST /api/v1/auth/register`)
```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-client-api-key" \
  -d '{
    "externalId": "usr_alice_123",
    "password": "SecurePassword123!",
    "attributes": {
      "department": "Engineering",
      "region": "US-WEST"
    }
  }'
```

#### Response (`201 Created`):
```json
{
  "token": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900000,
  "subjectId": "a1b2c3d4-0000-0000-0000-000000000001",
  "externalId": "usr_alice_123"
}
```

Store `subjectId` and include the token in subsequent requests:
```http
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
```

---

### Step 3: Register Domain Resources
When a user creates an asset (document, project, bank account, medical record) in your system, register it with Permissio:

#### Create Resource (`POST /api/v1/resources`)
```bash
curl -X POST http://localhost:8080/api/v1/resources \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-client-api-key" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "resourceType": "DOCUMENT",
    "externalId": "doc_financial_q3",
    "attributes": {
      "confidentiality": "HIGH",
      "department": "Engineering"
    }
  }'
```

#### Response (`201 Created`):
```json
{
  "id": "b2c3d4e5-0000-0000-0000-000000000002",
  "clientId": "11111111-1111-1111-1111-111111111111",
  "resourceType": "DOCUMENT",
  "externalId": "doc_financial_q3",
  "attributes": { "confidentiality": "HIGH", "department": "Engineering" },
  "createdAt": "2026-08-23T22:00:00Z"
}
```

---

### Step 4: Grant Roles & Relationships
Assign the user a hierarchical relation over the resource (`OWNER`, `MANAGER`, `LEAD`, `MEMBER`):

#### Create Relationship (`POST /api/v1/relationships`)
```bash
curl -X POST http://localhost:8080/api/v1/relationships \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-client-api-key" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "subjectId": "a1b2c3d4-0000-0000-0000-000000000001",
    "resourceId": "b2c3d4e5-0000-0000-0000-000000000002",
    "relation": "OWNER"
  }'
```

#### Relationship Hierarchy Capabilities:
- **`OWNER`**: `CREATE`, `READ`, `UPDATE`, `DELETE`, `APPROVE`, `REJECT`
- **`MANAGER`**: `CREATE`, `READ`, `UPDATE`
- **`LEAD`**: `CREATE`, `READ`
- **`MEMBER`**: `READ`

---

### Step 5: Check Permissions in Your Code (`POST /api/v1/authorize`)
Whenever a request hits your application controller, verify authorization:

```bash
curl -X POST http://localhost:8080/api/v1/authorize \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-client-api-key" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{
    "subjectId": "a1b2c3d4-0000-0000-0000-000000000001",
    "resourceId": "b2c3d4e5-0000-0000-0000-000000000002",
    "action": "UPDATE"
  }'
```

#### Response (`200 OK`):
```json
{
  "allowed": true,
  "reason": null,
  "evaluator": "RebacEvaluator"
}
```

---

## Multi-Language Code Examples

### 1. Java / Spring Boot Integration

```java
@Service
public class PermissioClient {

    private final RestClient restClient;

    public PermissioClient(@Value("${permissio.base-url}") String baseUrl,
                           @Value("${permissio.api-key}") String apiKey) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("X-API-Key", apiKey)
                .build();
    }

    public boolean isAllowed(UUID subjectId, UUID resourceId, String action, String jwtToken) {
        AuthorizeRequest request = new AuthorizeRequest(subjectId, resourceId, action);

        AuthorizeResponse response = restClient.post()
                .uri("/authorize")
                .header("Authorization", "Bearer " + jwtToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(AuthorizeResponse.class);

        return response != null && response.allowed();
    }

    public record AuthorizeRequest(UUID subjectId, UUID resourceId, String action) {}
    public record AuthorizeResponse(boolean allowed, String reason, String evaluator) {}
}
```

#### Spring MVC Controller Protection:
```java
@PutMapping("/documents/{id}")
public ResponseEntity<?> updateDocument(@PathVariable UUID id, 
                                        @RequestHeader("Authorization") String token,
                                        @AuthenticationPrincipal User user) {
    if (!permissioClient.isAllowed(user.getSubjectId(), id, "UPDATE", token)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Permission denied");
    }
    // Proceed with business logic
    return ResponseEntity.ok(documentService.update(id));
}
```

---

### 2. TypeScript / Node.js (Express / Fastify)

```typescript
import axios from 'axios';

const permissio = axios.create({
  baseURL: process.env.PERMISSIO_BASE_URL || 'http://localhost:8080/api/v1',
  headers: {
    'X-API-Key': process.env.PERMISSIO_API_KEY,
    'Content-Type': 'application/json',
  },
});

export async function checkPermission(
  subjectId: string,
  resourceId: string,
  action: 'CREATE' | 'READ' | 'UPDATE' | 'DELETE' | 'APPROVE' | 'REJECT',
  jwtToken: string
): Promise<boolean> {
  try {
    const res = await permissio.post(
      '/authorize',
      { subjectId, resourceId, action },
      { headers: { Authorization: `Bearer ${jwtToken}` } }
    );
    return res.data.allowed === true;
  } catch (error) {
    console.error('Authorization check failed:', error);
    return false; // Fail-closed
  }
}
```

#### Express Route Middleware:
```typescript
app.delete('/api/documents/:id', async (req, res) => {
  const token = req.headers.authorization?.replace('Bearer ', '');
  const isAllowed = await checkPermission(
    req.user.subjectId,
    req.params.id,
    'DELETE',
    token
  );

  if (!isAllowed) {
    return res.status(403).json({ error: 'Forbidden: Insufficient permissions' });
  }

  // Delete document logic...
  res.status(204).send();
});
```

---

### 3. Python (FastAPI / Requests)

```python
import os
import httpx
from fastapi import HTTPException, status

PERMISSIO_BASE_URL = os.getenv("PERMISSIO_BASE_URL", "http://localhost:8080/api/v1")
PERMISSIO_API_KEY = os.getenv("PERMISSIO_API_KEY", "your-client-api-key")

async def require_permission(subject_id: str, resource_id: str, action: str, jwt_token: str):
    async with httpx.AsyncClient() as client:
        resp = await client.post(
            f"{PERMISSIO_BASE_URL}/authorize",
            headers={
                "X-API-Key": PERMISSIO_API_KEY,
                "Authorization": f"Bearer {jwt_token}",
                "Content-Type": "application/json",
            },
            json={
                "subjectId": subject_id,
                "resourceId": resource_id,
                "action": action,
            },
            timeout=2.0,
        )

        if resp.status_code != 200 or not resp.json().get("allowed", False):
            raise HTTPException(
                status_code=status.HTTP_403_FORBIDDEN,
                detail="Forbidden: Permission denied by Permissio"
            )
```

---

## Best Practices & Production Guidelines

1. **Fail-Closed Principle:** Always default to denying access if Permissio returns an error or network timeout occurs.
2. **Short-Lived Read Cache:** For high-throughput read paths, cache positive `/authorize` decisions locally (e.g. In-Memory Caffeine / Redis with a 5-10 second TTL).
3. **Trace Propagation:** Pass the `X-Trace-Id` header from your incoming requests to Permissio to correlate distributed logs in Jaeger.
4. **Never Share API Keys Across Microservices:** Each consuming service should have its own registered client record for isolated auditing.
