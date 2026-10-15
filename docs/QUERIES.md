# DevTrace AI — Cypher Query Catalog

This catalog documents every Cypher query utilized by DevTrace AI, including query name, operational purpose, parameters, edge directionality, and verified sample output from the live `devtrace` graph.

All queries are executed with bounded traversal depths and strictly parameterized maps (`Map<String, Object>`) to eliminate Cypher injection and prevent unbounded graph traversals.

---

## 1. Catalog Queries

### 1.1 `LOAD_ALL_SERVICES`
- **Method**: `GraphRepository.loadServices(graphName)`
- **Purpose**: Loads all registered microservices, databases, and caches with team ownership, criticality tier, service type, and alias lists for in-memory entry-point matching.
- **Parameters**: None
- **Direction**: Node scan.
- **Cypher**:
  ```cypher
  MATCH (s:Service)
  RETURN s.id AS id, s.name AS name, s.team AS team, s.serviceType AS serviceType, s.criticalTier AS tier, s.aliases AS aliases
  ORDER BY s.id ASC
  ```
- **Verified Sample Result**:
  ```
  id: service-checkout | name: checkout-service | team: Ordering | serviceType: MICROSERVICE | tier: 1 | aliases: [checkout-service, checkout, web-checkout, cart-checkout]
  id: service-payment  | name: payment-service  | team: Payments Core | serviceType: MICROSERVICE | tier: 1 | aliases: [payment-service, payment, payments, checkout-payment, payment-gateway]
  ```

### 1.2 `LOAD_ALL_ERROR_SIGNATURES`
- **Method**: `GraphRepository.loadErrorSignatures(graphName)`
- **Purpose**: Loads error signature patterns, error types, keyword lists, and historically affected services for entry-point resolution and tie-breaking.
- **Parameters**: None
- **Direction**: Traverses `(Incident)-[:EXHIBITS]->(ErrorSignature)` and `(Incident)-[:AFFECTS]->(Service)`.
- **Cypher**:
  ```cypher
  MATCH (e:ErrorSignature)
  OPTIONAL MATCH (i:Incident)-[:EXHIBITS]->(e)
  OPTIONAL MATCH (i)-[:AFFECTS]->(s:Service)
  RETURN e.id AS id, e.pattern AS pattern, e.errorType AS errorType, e.keywords AS keywords, collect(DISTINCT s.id) AS associatedServices
  ORDER BY e.id ASC
  ```
- **Verified Sample Result**:
  ```
  id: err-sig-payment-pool-timeout
  pattern: JedisConnectionException / PoolTimeoutException: Could not get a resource from the pool
  errorType: TIMEOUT
  keywords: [payment timeout, payment timeouts, connection pool timeout, jedis pool timeout, pool timeout, pool exhausted, read timeout]
  associatedServices: [service-payment]
  ```

---

## 2. Topological Traversal Queries

### 2.1 `FIND_DOWNSTREAM_DEPENDENCIES`
- **Method**: `GraphRepository.findDependencies(graphName, serviceId, maxDepth, limit)`
- **Purpose**: Traverses outgoing `DEPENDS_ON` edges to discover all downstream services reachable from a symptom service. Used to bound the candidate cause neighborhood.
- **Parameters**:
  - `serviceId` (string): Symptom service ID (e.g. `'service-checkout'`).
  - `limit` (int): Maximum records (e.g. `50`).
  - `maxDepth` (int): Traversal depth bound (clamped $1..10$, default `3`).
- **Direction**: `(src)-[:DEPENDS_ON*1..%d]->(callee)` (Operational caller -> callee).
- **Cypher**:
  ```cypher
  MATCH path = (src:Service {id: $serviceId})-[:DEPENDS_ON*1..%d]->(dep:Service)
  WITH dep, min(length(path)) AS minHops
  ORDER BY minHops ASC, dep.id ASC
  LIMIT $limit
  RETURN dep.id AS id, dep.name AS name, dep.serviceType AS serviceType, minHops AS hops
  ```
- **Verified Sample Result** (`service-checkout`, depth 3):
  ```
  - service-inventory        (inventory-service, MICROSERVICE) at 1 hops
  - service-order            (order-service, MICROSERVICE)     at 1 hops
  - service-payment          (payment-service, MICROSERVICE)   at 2 hops
  - service-postgres-orders  (postgres-orders, DATABASE)       at 2 hops
  - service-postgres-payments(postgres-payments, DATABASE)     at 3 hops
  - service-redis-cache      (redis-cache, CACHE)              at 3 hops
  ```

### 2.2 `FIND_UPSTREAM_DEPENDENTS` (Blast Radius)
- **Method**: `GraphRepository.findDependents(graphName, serviceId, maxDepth, limit)`
- **Purpose**: Traverses incoming `DEPENDS_ON` edges from callers to compute the blast radius of a failing service.
- **Parameters**:
  - `serviceId` (string): Suspect service ID (e.g. `'service-payment'`).
  - `limit` (int): Maximum records (e.g. `20`).
  - `maxDepth` (int): Traversal depth bound (clamped $1..10$, default `4`).
- **Direction**: `(caller)-[:DEPENDS_ON*1..%d]->(target)` (Callee <- Caller).
- **Direction Note**: In operational microservices, calls flow caller -> callee. When a callee degrades, failure cascade propagates in the *exact reverse direction* (upstream to callers).
- **Cypher**:
  ```cypher
  MATCH path = (caller:Service)-[:DEPENDS_ON*1..%d]->(target:Service {id: $serviceId})
  WITH caller, min(length(path)) AS minHops
  ORDER BY minHops ASC, caller.id ASC
  LIMIT $limit
  RETURN caller.id AS id, caller.name AS name, caller.serviceType AS serviceType, minHops AS hops
  ```
- **Verified Sample Result** (`service-payment`, depth 4):
  ```
  Direct Dependents (1 hop):
    - service-order (order-service, MICROSERVICE)
  Indirect Dependents (2+ hops):
    - service-checkout (checkout-service, MICROSERVICE) at 2 hops
    - service-gateway (api-gateway, MICROSERVICE)       at 3 hops
  Excluded: Databases and caches (callees, not callers).
  ```

### 2.3 `FIND_SHORTEST_DEPENDENCY_PATH`
- **Method**: `GraphRepository.findDependencyPath(graphName, fromServiceId, toServiceId, maxDepth)`
- **Purpose**: Discovers the shortest directed invocation path from the symptom service to a candidate root cause.
- **Parameters**:
  - `fromId` (string): Caller / symptom service ID (e.g. `'service-checkout'`).
  - `toId` (string): Callee / candidate service ID (e.g. `'service-payment'`).
  - `maxDepth` (int): Max path length (default `4`).
- **Direction**: Directed `shortestPath((src)-[:DEPENDS_ON*1..%d]->(tgt))` within `WITH`/`RETURN`.
- **Cypher**:
  ```cypher
  MATCH (src:Service {id: $fromId}), (tgt:Service {id: $toId})
  WITH shortestPath((src)-[:DEPENDS_ON*1..%d]->(tgt)) AS p
  WHERE p IS NOT NULL
  RETURN [n in nodes(p) | n.id] AS path, length(p) AS hopCount
  ```
- **Verified Sample Result** (`service-checkout` -> `service-payment`):
  ```
  path: [service-checkout, service-order, service-payment]
  hopCount: 2
  ```

---

## 3. Historical Retrieval & Evidence Queries

### 3.1 `FIND_SIMILAR_INCIDENTS_STRUCTURED`
- **Method**: `GraphRepository.findSimilarIncidents(graphName, errorSignatureId, candidateServiceIds)`
- **Purpose**: Deterministic structured retrieval of historical incidents that:
  1. Exhibit the exact matching `ErrorSignature`.
  2. Affect a service within the topological candidate neighborhood.
  3. Include a confirmed `Resolution` record if one exists.
- **Parameters**:
  - `sigId` (string): Matched error signature ID (e.g. `'err-sig-payment-pool-timeout'`).
  - `serviceIds` (List<String>): Candidate services in neighborhood.
- **Direction**: `(Incident)-[:EXHIBITS]->(ErrorSignature)`, `(Incident)-[:AFFECTS]->(Service)`, `(Resolution)-[:RESOLVES]->(Incident)`.
- **Cypher**:
  ```cypher
  MATCH (i:Incident)-[:EXHIBITS]->(e:ErrorSignature {id: $sigId})
  MATCH (i)-[:AFFECTS]->(s:Service)
  WHERE s.id IN $serviceIds
  OPTIONAL MATCH (r:Resolution)-[:RESOLVES]->(i)
  RETURN i.id AS incidentId, i.title AS title, i.status AS status, e.id AS errorSignatureId,
         collect(DISTINCT s.id) AS affectedServiceIds,
         r.id AS resId, r.title AS resTitle, r.remediationType AS resRemediationType,
         r.actionTaken AS resActionTaken, r.confirmedAt AS resConfirmedAt, r.confirmedBy AS resConfirmedBy
  ORDER BY i.reportedAt DESC
  ```
- **Verified Sample Result**:
  ```
  incidentId: inc-2026-08-12-pay
  title: Payment timeout surge during peak checkout
  errorSignatureId: err-sig-payment-pool-timeout
  affectedServiceIds: [service-payment]
  status: RESOLVED
  resId: res-pay-timeout-fix
  resTitle: Increase Jedis pool timeout and rollback connection limit
  resActionTaken: Increased Redis connection pool timeout from 2000ms to 10000ms and rolled back connection pool cap.
  resRemediationType: CONFIG_CHANGE
  resConfirmedAt: 1786525200000 (2026-08-12 09:00:00 UTC)
  resConfirmedBy: sre-oncall
  ```

### 3.2 `FIND_ALL_DEPLOYMENTS_IN_WINDOW`
- **Method**: `GraphRepository.findAllDeploymentsInWindow(graphName, windowStart, windowEnd)`
- **Purpose**: Retrieves all deployments in $[T_{\text{start}}, T_{\text{end}}]$ across the entire graph. The engine partitions results into candidate deployments (services in neighborhood) and `ignoredChanges` (services outside neighborhood).
- **Parameters**:
  - `windowStart` (long): Epoch ms (reference time minus window, e.g. $T_0 - 24\text{h}$).
  - `windowEnd` (long): Epoch ms (reference time $T_0$). Deployments after $T_0$ are ignored.
- **Direction**: `(Deployment)-[:DEPLOYED_TO]->(Service)`.
- **Cypher**:
  ```cypher
  MATCH (d:Deployment)-[:DEPLOYED_TO]->(s:Service)
  WHERE d.deployedAt >= $windowStart AND d.deployedAt <= $windowEnd
  RETURN d.id AS deploymentId, s.id AS serviceId, d.version AS version,
         d.deployedAt AS deployedAt, d.environment AS environment, d.deployedBy AS deployedBy
  ORDER BY d.deployedAt DESC
  ```
- **Verified Sample Result**:
  ```
  dep-inventory-v1.5.0    | service-inventory    | v1.5.0 | 1792057800000 (2026-10-15 09:50 UTC) -> candidate
  dep-notification-v1.8.0 | service-notification | v1.8.0 | 1792057500000 (2026-10-15 09:45 UTC) -> ignoredChanges
  dep-payment-v2.4.1      | service-payment      | v2.4.1 | 1792056600000 (2026-10-15 09:30 UTC) -> candidate
  ```

### 3.3 `VERIFY_RECORD_EXISTS`
- **Method**: `GraphRepository.recordExists(graphName, recordId)`
- **Purpose**: Verifies that every evidence item in the investigation report references a graph record ID that actually exists in FalkorDB.
- **Parameters**:
  - `recordId` (string): Record ID (e.g. `'dep-payment-v2.4.1'`).
- **Direction**: Direct index lookup on unique node ID.
- **Cypher**:
  ```cypher
  MATCH (n {id: $recordId}) RETURN count(n) > 0 AS exists
  ```
- **Verified Sample Result**:
  ```
  recordId: 'dep-payment-v2.4.1' -> true
  recordId: 'inc-2026-08-12-pay'  -> true
  recordId: 'res-pay-timeout-fix' -> true
  ```
