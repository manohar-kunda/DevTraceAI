# DevTrace AI — REST API Reference

The DevTrace AI REST API provides HTTP endpoints for automated incident creation, deterministic graph-backed root cause investigation, resolution write-back, and blast-radius dependency analysis.

---

## 1. Authentication & Security

- **Shared Secret Token**: Required for write-back mutation endpoints (`POST /api/incidents/{id}/resolution`).
- **Header**: `X-DevTrace-Token: <token>`
- **Server Configuration**: Configured via the `DEVTRACE_API_TOKEN` environment variable.
- **Fail-Closed**: If `DEVTRACE_API_TOKEN` is unset or blank on the server, the resolution endpoint is completely disabled (returns `401 Unauthorized`).
- **Timing Safe**: Compared using `MessageDigest.isEqual` to prevent timing attacks. Tokens are never logged or echoed in error messages.
- **CORS**: Restricted to the configured developer UI origin (`http://localhost:5173` by default; never `*`).

---

## 2. Standard Error Envelope (`ProblemDetail` — RFC 7807)

All errors return a consistent JSON body formatted according to RFC 7807:

```json
{
  "type": "https://devtrace.ai/errors/bad-request",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed for one or more request fields.",
  "instance": "/api/incidents",
  "fieldErrors": {
    "description": "description cannot exceed 2000 characters"
  }
}
```

### Status Code Mappings
| Status Code | Title | Trigger Condition |
| :--- | :--- | :--- |
| **`400 Bad Request`** | Bad Request | Validation constraints violated (`@NotBlank`, `@Size`), illegal arguments. |
| **`401 Unauthorized`** | Unauthorized | Missing or invalid `X-DevTrace-Token` header, or `DEVTRACE_API_TOKEN` unset. |
| **`404 Not Found`** | Not Found | Target incident, root cause service, or deployment node does not exist in FalkorDB. |
| **`409 Conflict`** | Conflict | Incident or resolution ID exists with conflicting / differing payload. |
| **`503 Service Unavailable`** | Service Unavailable | FalkorDB database is stopped or network connection is refused (no stack traces leaked). |
| **`500 Internal Server Error`** | Internal Server Error | Unexpected server error. Correlation ID returned to client and logged on server. |

---

## 3. Endpoints

### 3.1 Health Check
- **Endpoint**: `GET /api/health`
- **Purpose**: Liveness and database connectivity verification.
- **Graph Operation**: Executes `RETURN 1` against FalkorDB.
- **Status Codes**: `200 OK` (healthy), `503 Service Unavailable` (database unreachable).

```cmd
curl.exe -s http://localhost:8085/api/health
```

---

### 3.2 Demo Golden Incident
- **Endpoint**: `GET /api/demo/golden-incident`
- **Purpose**: Returns the pre-configured golden scenario payload for one-click frontend demo loading.
- **Status Codes**: `200 OK`.

```cmd
curl.exe -s http://localhost:8085/api/demo/golden-incident
```

---

### 3.3 Create Incident
- **Endpoint**: `POST /api/incidents`
- **Purpose**: Creates an `OPEN` incident node in FalkorDB. Idempotent on `incidentId`.
- **Validation**:
  - `description`: Required, 1..2000 characters.
  - `serviceHint`: Optional, max 200 characters.
  - `errorHint`: Optional, max 200 characters.
  - `startedAt`: Optional epoch milliseconds (defaults to server clock).
  - `incidentId`: Optional custom ID, max 100 characters.
- **Graph Operation**:
  - `MERGE (i:Incident {id: $id}) ...`
  - Links optional `(i)-[:AFFECTS]->(s:Service)` and `(i)-[:EXHIBITS]->(e:ErrorSignature)`.
- **Status Codes**: `201 Created` (newly created), `200 OK` (idempotent repeat), `409 Conflict` (differing payload for same ID), `400 Bad Request`.

```cmd
curl.exe -s -X POST http://localhost:8085/api/incidents \
  -H "Content-Type: application/json" \
  --data-binary @docs/examples/create-incident.json
```

---

### 3.4 Get Incident Status
- **Endpoint**: `GET /api/incidents/{id}`
- **Purpose**: Retrieves incident status and any attached confirmed resolution.
- **Graph Operation**: Reads `Incident` node and optional `(Resolution)-[:RESOLVES]->(Incident)`.
- **Status Codes**: `200 OK`, `404 Not Found`.

```cmd
curl.exe -s http://localhost:8085/api/incidents/inc-2026-10-15-checkout-001
```

---

### 3.5 Run Investigation (Read-Only)
- **Endpoint**: `POST /api/incidents/{id}/investigation`
- **Purpose**: Executes deterministic topological investigation and historical pattern matching. **Read-only: modifies no state in FalkorDB.**
- **Behavior**:
  - `startedAt` defaults to incident's `reportedAt`. If absent, defaults to server `Clock` and records in `report.assumptions`.
  - Excludes the investigated incident `{id}` from historical matches.
- **Status Codes**: `200 OK`, `404 Not Found`, `503 Service Unavailable`.

```cmd
curl.exe -s -X POST http://localhost:8085/api/incidents/inc-2026-10-15-checkout-001/investigation
```

---

### 3.6 Confirm Resolution (Protected Mutation)
- **Endpoint**: `POST /api/incidents/{id}/resolution`
- **Security**: Requires header `X-DevTrace-Token: <DEVTRACE_API_TOKEN>`.
- **Validation**:
  - `rootCauseServiceId`: Required, must exist in FalkorDB.
  - `title`: Required, 1..200 characters.
  - `remediationType`: Required, 1..50 characters (`CONFIG_CHANGE`, `ROLLBACK`, `RESTART`, etc.).
  - `actionTaken`: Required, 1..2000 characters.
  - `confirmedBy`: Required, 1..100 characters.
  - `triggeringDeploymentId`: Optional, must exist in FalkorDB if provided.
- **Graph Operation**:
  - Transitions `Incident.status = 'RESOLVED'`.
  - Creates `(Resolution {id: 'res-' + $id, source: 'user-confirmed', ...})`.
  - Atomically creates relationships:
    - `(Resolution)-[:RESOLVES]->(Incident)`
    - `(Resolution)-[:ADDRESSES]->(Service)`
    - `(Resolution)-[:TRIGGERED_BY]->(Deployment)` (optional)
- **Status Codes**:
  - `201 Created`: Successfully confirmed and persisted.
  - `200 OK`: Idempotent identical repeat confirmation.
  - `400 Bad Request`: Field validation error.
  - `401 Unauthorized`: Token missing, invalid, or unset on server.
  - `404 Not Found`: Unknown incident or unknown root cause service ID.
  - `409 Conflict`: Incident already resolved with conflicting resolution payload.

```cmd
curl.exe -s -X POST http://localhost:8085/api/incidents/inc-2026-10-15-checkout-001/resolution \
  -H "Content-Type: application/json" \
  -H "X-DevTrace-Token: <redacted>" \
  --data-binary @docs/examples/confirm-resolution.json
```

---

### 3.7 Blast Radius Analysis
- **Endpoint**: `GET /api/services/{id}/blast-radius?maxDepth=3`
- **Purpose**: Calculates direct and indirect upstream callers that depend on target service.
- **Parameters**: `maxDepth` (integer, server-side clamped between 1 and 10, default 3).
- **Status Codes**: `200 OK`, `404 Not Found`.

```cmd
curl.exe -s "http://localhost:8085/api/services/service-payment/blast-radius?maxDepth=3"
```
