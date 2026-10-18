# DevTrace AI — Video Demo Recording Script (3-4 Minutes)

> **Scenario**: The Golden Scenario — Post-Deployment Cascading Failure & Resolution Write-Back  
> **Target Duration**: 3 minutes 30 seconds  
> **Audience**: Hackathon Judges (Track 01: Agents That Act on Connected Data)

---

## Pre-Recording Checklist
- [ ] Docker Desktop is running and FalkorDB container is active (`docker compose ps`).
- [ ] Database reset has been run to ensure the clean golden state (`devtrace` graph has 24 nodes, 26 relationships, and zero user-confirmed resolutions).
- [ ] Backend is running on port 8085 (`mvn spring-boot:run`).
- [ ] Frontend dev server is running on port 5173 (`npm run dev`).
- [ ] Browser window is sized to 1080p (1920x1080) with zoom set to 100% or 110% for clear readability.
- [ ] `DEVTRACE_API_TOKEN` is copied to clipboard or ready to type.

---

## Scene 1: Problem Statement & Graph Topology (0:00 – 0:45)
- **Visual**: Browser on `http://localhost:5173`. Point out the header and the **`● SYNTHETIC DEMO DATA`** pill.
- **Narration**:
  > "Welcome to DevTrace AI. In modern distributed systems, when an incident occurs, alerts often fire on client-facing services like checkout, while the true root cause is buried multiple network hops downstream.
  > Instead of sifting through fragmented logs, DevTrace AI acts directly on a connected knowledge graph in FalkorDB—connecting microservice dependencies, recent deployment changes, and historical resolutions into an actionable investigation engine."

---

## Scene 2: Loading Golden Scenario & Free-Text Resolution (0:45 – 1:30)
- **Action**: Click **"Load Demo Golden Incident"**.
- **Visual**: The incident description fills in: *"Checkout requests are failing after the latest deployment and payment timeouts have increased"*. Point out that **serviceHint** and **errorHint** remain completely blank.
- **Action**: Click **"Investigate Incident"**.
- **Visual**: The investigation progress finishes and the report renders instantly.
- **Narration**:
  > "Notice that we provided zero manual hints. Our entry point resolver analyzes the raw alert text against the FalkorDB catalog and resolves `checkout-service` as the symptom entry point and `err-sig-payment-pool-timeout` as the matched error signature."

---

## Scene 3: Explainable Root-Cause Ranking & Graph Path (1:30 – 2:30)
- **Action**: Scroll smoothly through **Section B (Observed Facts)**, **Section C (Ranked Hypotheses)**, and **Section D (Failure Propagation Path)**.
- **Visual**: Highlight the `#1 payment-service` (**STRONG TIER**) and `#2 inventory-service` (**MODERATE TIER**).
- **Narration**:
  > "DevTrace AI evaluates the topological neighborhood downstream from checkout. 
  > Notice the ranking: `inventory-service` deployed 10 minutes ago, but it has no error signature matches, placing it in the MODERATE tier. 
  > `payment-service` deployed 30 minutes ago, but it matched a historical incident where connection pool timeouts occurred, placing it as our #1 STRONG candidate.
  > In Section D, FalkorDB traces the exact 2-hop directed dependency path: checkout depends on order, which depends on payment. The failure propagated upstream in reverse."

---

## Scene 4: Closed-Loop Human Confirmation & Resolution Write-Back (2:30 – 3:15)
- **Action**: Scroll down to **"2. Human-in-the-Loop Resolution Write-Back"**.
- **Action**: Fill or verify the exact resolution fields for Incident A (`inc-2026-10-15-checkout-001`):
  - **Root Cause Service**: `service-payment`
  - **Remediation Title**: `Emergency pool size rollback and idle timeout tune on payment datasource`
  - **Remediation Type**: `CONFIG_CHANGE`
  - **Action Taken**: `Restored HikariCP maximumPoolSize from 10 back to 50, raised connectionTimeout to 30000ms, and verified connection wait queue dropped to zero under load.`
  - **Confirmed By**: `sre-oncall-lead`
  - **Triggering Deployment**: `dep-payment-v2.4.1`
  - **DevTrace API Token**: Paste the token from your local `.env`.
- **Action**: Click **"Confirm Resolution & Evolve Graph"**.
- **Visual**: The green confirmation banner appears: `✔ Resolution Confirmed & Written to FalkorDB` showing resolution ID `res-inc-2026-10-15-checkout-001` with `source: user-confirmed`.
- **Narration**:
  > "Investigation engines should never persist speculative hypotheses. DevTrace AI requires an authenticated human SRE to confirm the actual fix.
  > Clicking Confirm executes an atomic Cypher write-back transaction in FalkorDB. It marks incident A RESOLVED, links the resolution to the payment service, and tags it with 'user-confirmed' provenance."

---

## Scene 5: Institutional Memory in Action — Investigating Incident B (3:15 – 3:45)
- **Action**: In the incident form at the top, enter Incident B:
  - **Incident ID / Title**: `inc-2026-10-15-checkout-002`
  - **Description**: `Checkout failure rates spiking again with persistent payment timeouts on card transactions`
  - Leave **serviceHint** and **errorHint** empty.
- **Action**: Click **"Investigate Incident"**.
- **Visual**: In Section B (Observed Facts / Matched Historical Incidents), observe that **both** resolutions are now returned in recency order:
  1. `inc-2026-10-15-checkout-001` with resolution `res-inc-2026-10-15-checkout-001` (`source: user-confirmed`): *"Emergency pool size rollback and idle timeout tune on payment datasource"*
  2. `inc-2026-08-12-pay` with resolution `res-pay-timeout-fix` (`source: synthetic`): *"Increase HikariCP pool size and connection timeout"*
- **Narration**:
  > "When a subsequent incident occurs, DevTrace AI's connected graph instantly surfaces the newly confirmed resolution as institutional memory. 
  > Notice also that Incident A never appears in its own history, preserving strict causal isolation.
  > That is closed-loop operational intelligence with FalkorDB."

