# Engineering Blog Outline: Building DevTrace AI on FalkorDB

> **Title**: Beyond Log Dumps: Building an SRE Incident Investigation Engine with Graph-Native Topology on FalkorDB  
> **Target Audience**: Backend Engineers, SREs, Graph Database Developers

---

## 1. Introduction: The Cascading Failure Dilemma
- The reality of microservice incidents: the service firing the P1 alert is almost never the service causing the failure.
- Why relational queries and log aggregators fail at topological reasoning:
  - Recursive queries (`WITH RECURSIVE`) are brittle and slow under cyclic microservice topologies.
  - Metrics dashboards don't understand directed call graphs or upstream blast radiuses.

---

## 2. Real Architecture Lessons & Dependency Pitfalls
*Drawn from real progress logs during development:*
- **The Spring Boot & Jedis Dependency Collision**:
  - `com.falkordb:jfalkordb:0.13.0` depends transitively on Jedis 5.x (`redis.clients:jedis:5.2.0`).
  - Spring Boot 4.x / Spring AI 2.x bom managed `redis.clients:jedis` to Jedis 8.0 alpha, resulting in NoSuchMethodError exceptions on socket initializers.
  - *Fix*: Pinning transitive dependency overrides in `pom.xml` to force binary compatibility.
- **Java 25 Runtime on Modern Stacks**:
  - Validating Java 25 LTS with latest Spring Boot WebMVC, Docker Compose v2, and binary Redis protocol sockets.
- **The Epoch Date Calculation Trap**:
  - Generating synthetic timestamps: how a 1-digit typo (`1760522400000` vs `1792058400000`) placed incidents in 2025 instead of 2026.
  - Adding integration test assertions that convert Unix milliseconds back to UTC calendar dates to guarantee story coherence.

---

## 3. Graph Modeling: Topology + Events + Resolutions
- Why a 3-legged schema works:
  1. **Structural Topology**: `(Service)-[:DEPENDS_ON]->(Service)`
  2. **Temporal Changes**: `(Deployment)-[:DEPLOYED_TO]->(Service)`
  3. **Operational Knowledge**: `(Resolution)-[:RESOLVES]->(Incident)` and `(Resolution)-[:ADDRESSES]->(Service)`
- The FalkorDB Cypher Quirks Discovered:
  - `shortestPath()` in FalkorDB must be evaluated within a `WITH` or `RETURN` projection clause, rather than inside arbitrary `MATCH` patterns.
  - Clean cycle handling using bounded paths `[:DEPENDS_ON*1..3]`.

---

## 4. Why We Chose Transparent Tiers Over Opaque Scores
- The temptation of ML: calculating a floating-point "root cause probability" of 0.87.
- Why SREs reject probabilistic black boxes during a 2 AM outage: they need deterministic, explainable reasons.
- The 4-Tier Evaluation Engine:
  - `STRONG`: In neighborhood + recent deployment + matching error signature historically.
  - `MODERATE`: In neighborhood + recent deployment + no signature match.
  - `WEAK`: In neighborhood + zero deployments.
  - `IGNORED`: Explicitly documenting why outside changes (e.g., `notification-service`) were excluded from consideration.

---

## 5. Closed-Loop Knowledge Evolution: Human-in-the-Loop Write-Back
- Machine suggestions are read-only; only human mitigations evolve the graph.
- The Atomic Write-Back Pattern:
  - Transitioning incident status to `RESOLVED`.
  - Creating `Resolution` node with `source: 'user-confirmed'` and immutable server timestamps.
  - Demonstrating Act 4: How a resolved incident immediately becomes historical training evidence for the next incident.

---

## 6. Conclusion & What's Next
- How graph databases turn institutional tribal knowledge into an active, querying participant in incident response.
- Future work: Exposing DevTrace AI graph primitives as an MCP (Model Context Protocol) tool provider for autonomous LLM agents.
