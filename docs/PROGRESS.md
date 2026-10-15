# DevTrace AI — Implementation Progress Log

## Checkpoint 1 — Phase 0 & Phase 1: Environment & Java-to-FalkorDB Connectivity
- **Date / Time**: 2026-10-15
- **Verified Status**: Database Connected & Query Executed (`BUILD SUCCESS`)
- **Environment**:
  - Windows 11
  - Java 25.0.4.1 LTS
  - Maven 3.9.16
  - Docker Desktop / Container: `devtrace-falkordb` (ports 6379, 3000)
  - Driver: `com.falkordb:jfalkordb:0.13.0`
- **What Was Done**:
  - Inspected workspace `C:\Users\manoh\DevTraceAI`.
  - Verified `pom.xml` configuration with Java 25 compiler release target.
  - Inspected and compiled `src\main\java\com\devtrace\FalkorConnectionTest.java`.
  - Authenticated against local FalkorDB using environment variable `FALKORDB_PASSWORD`.
  - Executed Cypher query: `MATCH (a:Service)-[:DEPENDS_ON]->(b:Service) RETURN a.name AS source, b.name AS target`.
  - Received live response: `OrderService -> PaymentService`.
- **Key Design Decision**:
  - Verify raw driver execution and Java 25 runtime socket compatibility prior to introducing Spring Boot scaffolding, isolating potential JVM/driver bugs from framework configuration.
  - Enforce externalized credentials via `System.getenv("FALKORDB_PASSWORD")` from Day 1 to ensure zero secrets in source code or repositories.
- **Problems & Fixes**:
  - Missing environment variable initially resulted in deliberate `IllegalStateException`. Verified that setting `FALKORDB_PASSWORD` cleanly establishes connection.

## Checkpoint 2 — Repository Hygiene and Reproducible FalkorDB Setup
- **Date / Time**: 2026-10-15
- **Verified Status**: Git initialized, repository hygiene configured, docker compose validated, initial commit staged.
- **What Was Done**:
  - Initialized Git repository.
  - Created `.gitignore` covering `target/`, `.env`, `.env.*` (excluding `.env.example`), IDE metadata (`.idea/`, `*.iml`, `.vscode/`), logs, and secrets.
  - Created `.env.example` with non-sensitive placeholders (`FALKORDB_HOST`, `FALKORDB_PORT`, `FALKORDB_PASSWORD`, `FALKORDB_GRAPH`, `DEVTRACE_API_TOKEN`).
  - Inspected running FalkorDB container image digest (`sha256:107455e862a824e45e8e9f5a7fbfdf5e00a08b14e13eae2577c72fd9e3f59ba6`) and inspected `/var/lib/falkordb/bin/run.sh` to verify authentications args (`FALKORDB_ARGS=--requirepass ${FALKORDB_PASSWORD}`).
  - Created `docker-compose.yml` with pinned FalkorDB image digest, volume persistence (`falkordb_data`), and port mappings `6379` & `3000`.
  - Validated syntax with `docker compose config`.
  - Scanned staged files for raw credential leaks; verified zero occurrences.
- **Key Design Decisions**:
  - Pin the exact container digest (`falkordb/falkordb@sha256:107455...`) rather than floating `:latest` tag to guarantee reproducible evaluation across reviewers.
  - Never store or hardcode secrets in compose files, source code, or version control.

## Checkpoint 2b — Multi-Arch Digest Verification, Compose Hardening & Controlled Database Swap
- **Date / Time**: 2026-10-15
- **Verified Status**: Multi-arch image verified, compose hardened, controlled swap executed, persistence validated across restart, secrets isolation confirmed.
- **What Was Done**:
  - Inspected image digest `falkordb/falkordb@sha256:107455e862a824e45e8e9f5a7fbfdf5e00a08b14e13eae2577c72fd9e3f59ba6` via `docker buildx imagetools inspect`. Confirmed it is an OCI multi-arch image index containing both `linux/arm64` (Apple Silicon / ARM) and `linux/amd64` (x86_64).
  - Enforced required password evaluation in `docker-compose.yml`: `${FALKORDB_PASSWORD:?set FALKORDB_PASSWORD in .env}`.
  - Parametrized host ports with defaults `${FALKORDB_PORT:-6379}` and `${FALKORDB_BROWSER_PORT:-3000}`, documenting them in `.env.example`.
  - Confirmed `/var/lib/falkordb/data` persistence directory from `/var/lib/falkordb/bin/run.sh` and container environment.
  - Verified `.env` is ignored by git (`git check-ignore -v .env`).
  - Renamed active git branch to `main` (`git branch -M main`).
  - Queried existing `devtrace-falkordb` container; verified graph held strictly the 2-node connectivity test (`OrderService -[DEPENDS_ON]-> PaymentService`).
  - Performed controlled swap: stopped `devtrace-falkordb` and renamed to `devtrace-falkordb-old` as safety rollback.
  - Started new instance via `docker compose up -d`, recreated 2-node test graph, and ran `FalkorConnectionTest` with Java 25 -> `BUILD SUCCESS`.
  - Tested volume persistence: executed `docker compose restart`, re-ran Java connectivity test -> `BUILD SUCCESS`, confirming data survived restart.
  - Executed secret scan using `findstr /s /m /l` across all workspace files; verified only untracked `.env` matched.
- **Key Design Decisions**:
  - Controlled swap over ephemeral ports: using compose for active local development tests reproducibility immediately while keeping the previous container as a non-destructive rollback.
  - Required password expansion (`${VAR:?error}`) prevents Docker Compose from silently starting an unauthenticated or partially configured container.

## Checkpoint 3 — Compatible Spring Boot + MCP Baseline & Health Probe
- **Date / Time**: 2026-10-15
- **Verified Status**: Spike verified, dependencies aligned, tests passed (`BUILD SUCCESS`), live 200/503 health probe verified.
- **What Was Done**:
  - Performed volume persistence verification across container recreation: executed `docker compose down` (no `-v`) followed by `docker compose up -d`, and proved data survived via `FalkorConnectionTest`.
  - Researched official Spring Boot & Spring AI compatibility matrix on Maven Central:
    - *Correction*: `spring-ai-starter-mcp-server-webmvc` is published on Maven Central across both 1.x (`1.0.0-M7` to `1.1.8`) and 2.x (`2.0.0-M1` to `2.0.1`).
    - Candidate (b) Spring Boot 4.1.1 + Spring AI 2.0.1 was confirmed by spike as the optimal choice to provide native Java 25 LTS baseline support with Spring Framework 7.x.
  - Executed compatibility spike in `scratch/spike-boot4` with Java 25.0.4.1: compiled cleanly and booted in 2.04 seconds.
  - Diagnosed transitive dependency collision: `jfalkordb:0.13.0` requires `jedis:8.0.1` (`autoNegotiateProtocol` method), whereas Spring Boot 4.1.1 managed `jedis.version` to `7.4.1`. Overrode `<jedis.version>8.0.1</jedis.version>` in `pom.xml` to eliminate `NoSuchMethodError`.
  - Implemented `EnvLoader` to load local `.env` values into System properties without overriding OS environment variables.
  - Configured `application.properties` with fallback defaults, setting `server.port=${SERVER_PORT:8085}` to avoid conflict with existing host service on 8080.
  - Created singleton `Driver` bean in `FalkorDbConfig` managing connection pooling over Redis RESP protocol.
  - Implemented `GET /api/health` in `HealthController` executing `RETURN 1 AS ping`, returning HTTP 200 when active and controlled HTTP 503 with sanitized error messages (no credentials or stack traces) when down.
  - Created `DevTraceApplicationTests` and `HealthControllerTest`, executed `mvn test` -> `BUILD SUCCESS` (2 tests run, 0 failures).
  - Executed live verification: `curl` returned 200 OK -> `docker compose stop` -> `curl` returned 503 DOWN -> `docker compose start` -> `curl` recovered to 200 OK.
- **Key Design Decisions**:
  - Align `jedis.version` to 8.0.1 via Maven property override to satisfy `jfalkordb` binary requirements without breaking Spring Boot BOM management.
  - Sanitize all error responses in `HealthController` to prevent credential or internal socket leakage when the database is unavailable.

## Checkpoint 4 — Graph Model, Golden Scenario & FalkorDB Capability Probe
- **Date / Time**: 2026-10-15
- **Verified Status**: Capability probe executed on `devtrace_test`, graph schema and golden scenario documented in `docs/`.
- **What Was Done**:
  - Executed runtime capability probe on isolated test graph `devtrace_test` (and cleaned up with `GRAPH.DELETE devtrace_test`):
    - `CALL dbms.procedures()` revealed native graph algorithm procedures: `algo.pageRank`, `algo.betweenness`, `algo.HarmonicCentrality`, `algo.BFS`, `algo.WCC`, `algo.MSF`, `algo.maxFlow`.
    - `shortestPath()` was verified: FalkorDB supports it in `WITH` and `RETURN` clauses (`WITH shortestPath((a)-[:DEPENDS_ON*]->(c)) AS p RETURN [n in nodes(p) | n.id] AS path, length(p) AS len`).
    - Centrality algorithms verified on live graph: `CALL algo.pageRank('Service', 'DEPENDS_ON')` and `CALL algo.betweenness()`.
    - Index & Constraint syntax: `CREATE INDEX FOR (s:Service) ON (s.id)` creates the supporting index; `GRAPH.CONSTRAINT CREATE <graph> UNIQUE NODE <label> PROPERTIES 1 id` establishes uniqueness. Attempted duplicate insertion verified that FalkorDB rejects duplicate keys with `unique constraint violation on node of type <Label>`.
    - Bounded traversals (`[:DEPENDS_ON*1..4]`) and idempotent updates (`MERGE ... ON CREATE SET ... ON MATCH SET ...`) were verified.
  - Authored `docs/GRAPH_MODEL.md`:
    - Designed schema backwards from 7 operational investigation questions.
    - Defined 5 node labels (`Service`, `Deployment`, `ErrorSignature`, `Incident`, `Resolution`) with required typed properties.
    - Explicitly defined `(A)-[:DEPENDS_ON]->(B)` as caller -> callee, and blast radius traversal via incoming edges (`<-[:DEPENDS_ON]-`).
    - Documented deterministic keyword/alias parsing with honest limitations.
  - Authored `docs/GOLDEN_SCENARIO.md`:
    - Established 5-act demonstration script with 9 synthetic services and a 4-hop chain (`api-gateway` to `postgres-payments`).
    - Defined distractor deployments and past incidents demonstrating structured matching superiority.
    - Specified expected ranked candidate causes, blast radius boundaries, insufficient evidence handling, resolution write-back evolution, and the graph-removal benchmark.
## Checkpoint 5 — Idempotent Seed Implementation and Graph Verification
- **Date / Time**: 2026-10-15
- **Verified Status**: Schema constraints operational, idempotent seeding verified on `devtrace_test` (6/6 ITs passed), `devtrace` seeded, unit/integration tests strictly separated (`mvn test` vs `mvn verify`).
- **What Was Done**:
  - **Part 0 (Design Self-Audit)**:
    - Re-audited `docs/GRAPH_MODEL.md` and `docs/GOLDEN_SCENARIO.md`. Standardized all service node IDs with `service-` prefix (`service-gateway`, `service-auth`, `service-checkout`, `service-order`, `service-inventory`, `service-payment`, `service-notification`, `service-postgres-orders`, `service-postgres-payments`, `service-redis-cache`).
    - Added `serviceType` property (`"MICROSERVICE"`, `"DATABASE"`, `"CACHE"`) to differentiate microservices from infrastructure nodes.
    - Verified chronological sanity: all deployments precede incident timestamps, and all resolution `confirmedAt` timestamps are subsequent to incident `reportedAt`.
    - Documented deterministic free-text keyword tie-break rules: longest multi-word token precedence (e.g. `"payment timeout"` over `"timeout"`), topological reachability scoping (discarding signatures on services outside the call chain), and structured hint overrides.
    - Hardened distractors: added `dep-inventory-v1.5.0` on `service-inventory` ($T - 10\text{ min}$, 1 hop from checkout, zero error signature match). Proved that topology + recency alone would rank inventory higher, while DevTrace AI's multi-factor formula correctly ranks `payment-service` #1 due to error signature affinity.
    - Verified Act 2 blast radius ground truth on `devtrace_test` using Cypher: confirmed incoming callers (`service-order` 1 hop, `service-checkout` 2 hops, `service-gateway` 3 hops) and verified exclusion of callees (`postgres-payments`, `redis-cache`) and unrelated branches.
    - Documented PageRank directionality on caller-callee edges and confirmed deferral from the MVP candidate ranking formula to prevent uncalibrated heuristics.
  - **Part A (Seed Implementation & Schema Bootstrap)**:
    - Removed temporary test class `FalkorConnectionTest.java`.
    - Created classpath resource `src/main/resources/seed/devtrace-golden-scenario.cypher` containing 50 idempotent Cypher statements (`MERGE ... ON CREATE SET ... ON MATCH SET ...`) with `source: 'synthetic'`.
    - Implemented `FalkorSchemaBootstrap`: creates supporting exact-match indexes, issues `GRAPH.CONSTRAINT CREATE <graph> UNIQUE NODE <label> PROPERTIES 1 id` via Jedis `sendCommand`, and polls `CALL db.constraints()` until all 5 unique constraints are operational.
    - Implemented `FalkorSeedService`: reads and parses statements from classpath, enforces safety whitelist (`devtrace`, `devtrace_test`) before any graph deletion, executes statements, and returns structured `SeedResult`.
    - Configured `devtrace.seed.enabled` and `devtrace.seed.reset` in `application.properties` and `.env.example`.
  - **Part B (Test Separation & Live Seeding)**:
    - Added `maven-failsafe-plugin` to `pom.xml`. `mvn test` executes only pure unit tests (`*Test.java`) ensuring builds do not fail if Docker is down. `mvn verify` executes integration tests (`*IT.java`).
    - Created `FalkorSeedIntegrationIT` executing against isolated graph `devtrace_test`:
      1. Initial seed creates exactly 24 nodes and 26 relationships.
      2. Seed idempotency: second execution produces zero duplicates.
      3. `ON MATCH SET` restores modified node properties.
      4. Unique constraint rejects duplicate node ID insertion with constraint violation error.
      5. Act 2 blast radius matches ground truth incoming callers and excludes non-callers.
      6. Reset safety check throws `IllegalArgumentException` on non-whitelisted graphs.
    - Queried `devtrace` graph and confirmed it held only the 2 old test nodes (`OrderService`, `PaymentService`).
    - Seeded `devtrace` with the golden scenario dataset (529ms). Verified live state via Cypher: 24 nodes, 26 relationships, 5 operational constraints.
- **Key Design Decisions**:
  - Whitelist-guarded reset: `ALLOWED_RESET_GRAPHS = {"devtrace", "devtrace_test"}` ensures production graphs can never be accidentally dropped.
  - Strict separation of test lifecycles (`mvn test` vs `mvn verify` via Failsafe) keeps standard build pipelines reliable even when database containers are not running.

## Checkpoint 6 — Graph Repository Layer, Entry-Point Resolution & Dependency Traversal
- **Date / Time**: 2026-10-15
- **Verified Status**: All tests passing (`mvn test` 9/9 unit tests; `mvn verify` 15/15 integration tests; total 24 tests passed). Live graph probe on `devtrace` verified.
- **What Was Done**:
  - **Part 0 (Audit Fixes & Ground Truth Calibration)**:
    - Audited Unix epoch timestamps with PowerShell `[DateTimeOffset]::FromUnixTimeMilliseconds(<val>).UtcDateTime`. Corrected seed data and scenario docs from 2025 to October 2026 (incident $T_0 = 1792058400000$ = 2026-10-15 10:00:00 UTC). Added date integrity assertions to `FalkorSeedIntegrationIT`.
    - Verified untruncated ID `service-postgres-payments` across seed Cypher, scenario documentation, and graph assertions.
    - Renamed ambiguous `err-sig-gateway-timeout` to `err-sig-payment-pool-timeout` (`JedisConnectionException / PoolTimeoutException: Could not get a resource from the pool`) to represent the exact technical failure mode.
    - Updated candidate cause scoping in `docs/GOLDEN_SCENARIO.md`: disconnected services (`notification-service`) are explicitly excluded from candidate evaluation.
    - Re-seeded live development graph `devtrace` with calibrated timestamps and signature names.
  - **Part A (Data Models & Graph Repository)**:
    - Created domain records in `com.devtrace.model`: `ServiceNode`, `ErrorSignatureNode`, `ServiceHop`, `DependentsResult`, `DependenciesResult`, `DependencyPath`, `MatchedEvidence`, `ResolvedService`, `ResolvedErrorSignature`, `EntryPointsResult`, `InvestigationRequest`.
    - Documented reverse failure-propagation semantics in `DependencyPath`: caller -> callee defines invocation; callee -> caller defines cascading failure propagation.
    - Implemented `GraphRepository` interface and `FalkorGraphRepository`:
      - Parameterized Cypher queries (`Map<String, Object>`) preventing injection.
      - Bounded traversal depths (1..10) and result limits (1..100).
      - Utilized `min(length(path)) AS minHops` to deduplicate services and return exact shortest hop distance in cyclic/multi-path topologies.
      - Utilized FalkorDB `shortestPath((src)-[:DEPENDS_ON*1..%d]->(tgt))` inside `WITH`/`RETURN` to find directed paths.
  - **Part B (Entry-Point Resolver)**:
    - Implemented `EntryPointResolver` and `DefaultEntryPointResolver`:
      - Case, punctuation, and plural normalization with unstemmed technical exceptions (`redis`, `postgres`, `gateway`, `ingress`, `access`).
      - Longest-phrase matching precedence (e.g. `"payment timeout"` matched before `"timeout"`).
      - Structured hint override support (`symptomServiceHint`, `errorSignatureHint`).
      - Candidate neighborhood tie-breaking for ambiguous error signatures.
      - Zero-guess contract (`hasConfidentMatches()`) ensuring unresolvable queries do not produce hallucinations.
    - Implemented `DefaultEntryPointResolverTest` (7 unit tests, 100% passed offline in `mvn test`).
  - **Part C (Integration Tests & Live Graph Verification)**:
    - Implemented `FalkorGraphRepositoryIT` (8 integration tests, 100% passed in `mvn verify` against `devtrace_test`).
    - Verified complete offline safety: `mvn test` runs pure unit tests without requiring Docker or network access; `mvn verify` runs integration tests against isolated test graph `devtrace_test`.
    - Executed live repository probe against `devtrace` graph:
      - `findDependents("service-payment", 4, 10)` returned callers `service-order` (1 hop), `service-checkout` (2 hops), `service-gateway` (3 hops), and excluded non-callers.
      - `findDependencies("service-checkout", 4, 10)` returned all 6 downstream dependencies.
      - `findDependencyPath("service-checkout", "service-payment", 4)` returned `service-checkout -> service-order -> service-payment` (2 hops).
      - `findDependencyPath("service-payment", "service-checkout", 4)` returned empty, confirming caller-to-callee directionality.
- **Key Design Decisions**:
  - *Reverse Failure-Propagation Principle*: Graph edges follow operational call chains `(caller)-[:DEPENDS_ON]->(callee)`, while blast radius and incident impact propagate backward `(callee)<-[:DEPENDS_ON]-(caller)`. Modeling callers as dependents correctly computes impact without mutating graph edge semantics.
  - *Single-Node Minimal Hop Deduplication*: In interconnected microservices, multiple paths of varying lengths can reach the same dependent. Grouping by node and aggregating `min(length(path))` ensures every affected service appears exactly once at its closest topological distance.
  - *Zero-Guess Entry Resolution*: Entry-point resolution never guesses. When text is ambiguous or lacks matches, DevTrace AI explicitly signals missing evidence rather than traversing random graph subtrees.

## Checkpoint 7 — Historical Incident Retrieval, Candidate Generation, Tiered Ranking & Report Engine
- **Date / Time**: 2026-10-15
- **Verified Status**: All 15 unit tests passed (`mvn test` in 10.95s); all 18 failsafe integration tests passed (`mvn verify` in 15.70s); total 33 tests passed with 0 failures, 0 errors. Full Act 1 report verified as JSON.
- **What Was Done**:
  - **A. Reference Time Isolation**:
    - Ranking logic never reads `System.currentTimeMillis()`. The reference timestamp is `request.startedAt()` from the caller. An injected `Clock` is used solely as fallback when missing, and that assumption is explicitly recorded in `report.assumptions()`.
  - **B. Historical Retrieval (`findSimilarIncidents`)**:
    - Implemented deterministic structured graph matching in `GraphRepository` and `FalkorGraphRepository`. Retrieval requires: (1) same `ErrorSignature`, (2) same affected service, (3) service inside candidate neighborhood. Wording similarity is strictly forbidden.
    - Matches return incident ID, title, error signature, affected services, status, match reasons, and the confirmed `Resolution` record (id, summary, steps, confirmedAt, confirmedBy). Only confirmed resolutions are treated as "known resolutions".
  - **C. Candidate Generation & Evidence Collection**:
    - Generated candidate neighborhood from symptom service plus downstream dependencies within configurable depth (default 3).
    - Collected verifiable `EvidenceItem` records for each candidate carrying exact FalkorDB entity IDs and repository query names:
      - Deployments within configurable window before `startedAt` (default 24h; deployments after `startedAt` ignored).
      - Error signature affinity through historical incidents.
      - Confirmed historical resolutions.
      - Hop distance from symptom service (informational only).
    - Deployments on disconnected services (e.g. `notification-service`) are segregated into `ignoredChanges` with reason `"recent changes outside the dependency neighborhood, not considered"` and are never ranked.
  - **D. Deterministic Rule-Based Tiering (Engine)**:
    - Implemented rule-based tiers (`STRONG`, `MODERATE`, `WEAK`), strictly rejecting weighted scores and confidence percentages.
    - Implemented deterministic sorting: Tier precedence -> recency descending -> hop distance ascending -> unique `serviceId` ascending.
    - Proximity is a tie-breaker only and never outranks evidence: in Act 1, `payment-service` (2 hops, 30m prior, signature match) outranks `inventory-service` (1 hop, 10m prior, zero signature match).
    - Implemented `INSUFFICIENT_EVIDENCE` status when no candidate reaches `MODERATE`: refuses to name a root cause and declares missing evidence.
  - **E. Domain Report Model (`com.devtrace.model`)**:
    - Implemented immutable Java records aligning with `docs/GOLDEN_SCENARIO.md`: `InvestigationReport`, `ObservedFacts`, `GraphRelationships`, `CandidateHypothesis`, `IgnoredChange`, `SuggestedNextStep`, `ResolutionRecord`, `SimilarIncidentRecord`, `DeploymentRecord`, `EvidenceItem`. Zero LLM dependencies anywhere in the pipeline.
  - **F. Documentation**:
    - Authored `docs/QUERIES.md`: cataloged all 8 Cypher queries with purpose, parameters, edge directionality, and live verified outputs.
    - Authored `docs/RANKING.md`: documented the tiering framework, tie-breaking rules, parameter configuration, and uncalibrated heuristic limitations.
    - Updated `docs/GOLDEN_SCENARIO.md` with verified tiers.
  - **G. Unit & Integration Testing**:
    - Unit tests (`DefaultInvestigationEngineTest`): 6 tests verifying all 4 tier combinations, recency ordering, deterministic tie-breaks, window boundaries, insufficient evidence path, and sensitivity invariance between 1h and 24h windows.
    - Integration tests (`InvestigationEngineIT`):
      - Act 1: Verified engine-computed order and tiers (payment #1 STRONG, inventory #2 MODERATE, notification in ignoredChanges, auth incident excluded, confirmed resolution next-step generated, all evidence IDs verified against FalkorDB).
      - Act 3: Verified insufficient evidence path declares missing signature, missing deployments, and missing past incidents.
      - Act 5: Verified empty graph produces `INSUFFICIENT_EVIDENCE` with zero candidates, then cleanly restores seeded state.
- **Key Design Decisions**:
  - *Rule-Based Tiers vs Weighted Scores*: Numeric confidence percentages provide an illusion of calibration. Discrete evidentiary tiers (`STRONG`, `MODERATE`, `WEAK`) give on-call engineers actionable explanations backed by verifiable graph evidence.
  - *Proximity as Tie-Break Only*: Topological distance is secondary to domain evidence. A service closer to the symptom with recent changes must not outrank a slightly farther service that exhibits the exact technical error signature.
  - *Zero-LLM Incident Analysis*: Core triage, ranking, dependency tracing, blast radius calculation, and resolution lookup execute entirely via deterministic Java algorithms and FalkorDB graph traversals, guaranteeing sub-second execution, zero hallucination, and full offline operation.
