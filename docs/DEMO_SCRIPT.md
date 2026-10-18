# DevTrace AI — Video Demo Recording Script (3-4 Minutes)

> **Scenario**: The Golden Scenario — Post-Deployment Cascading Failure & Resolution Write-Back  
> **Target Duration**: 3 minutes 30 seconds  
> **Audience**: Hackathon Judges (Track 01: Agents That Act on Connected Data)

---

## Pre-Recording Checklist
- [ ] Docker Desktop is running and FalkorDB container is active (`docker compose ps`).
- [ ] Database reset has been run to ensure the clean golden state (`devtrace` graph has 24 nodes, 26 relationships, and zero user-confirmed resolutions):
  ```bash
  # PowerShell:
  $env:DEVTRACE_SEED_ENABLED="true"; $env:DEVTRACE_SEED_RESET="true"; mvn spring-boot:run
  # Linux/macOS:
  DEVTRACE_SEED_ENABLED=true DEVTRACE_SEED_RESET=true mvn spring-boot:run
  ```
- [ ] Backend is running on port 8085 (`mvn spring-boot:run`).
- [ ] Frontend dev server is running on port 5173 (`npm run dev` in `frontend/`).
- [ ] Browser window is sized to 1080p (1920x1080) with zoom set to 100% or 110% for clear readability.
- [ ] **Copy API token to clipboard before recording**: Copy your `DEVTRACE_API_TOKEN` value from `.env` to your system clipboard **before starting the screen recording** so you never open, view, or show `.env` on camera.

---

## Scene 1: Problem Statement & Graph Topology (0:00 – 0:45)
- **Visual**: Browser on `http://localhost:5173`. Point out the header and the **`● SYNTHETIC DEMO DATA`** pill.
- **Narration**:
  > "Welcome to DevTrace AI. In modern distributed systems, when an incident occurs, alerts often fire on client-facing services like checkout, while the true root cause is buried multiple network hops downstream.
  > Instead of sifting through fragmented logs, DevTrace AI acts directly on a connected knowledge graph in FalkorDB—connecting microservice dependencies, recent deployment changes, and historical resolutions into an actionable investigation engine."

---

## Scene 2: Loading Golden Scenario & Free-Text Resolution (0:45 – 1:30)
- **Visual**: Step 1 (**Describe**) is active on the navigation stepper.
- **Action**: Click **"Load demo incident A (DEMO)"**.
- **Visual**: The incident description fills in: *"Checkout requests are failing after the latest deployment and payment timeouts have increased"*. Advanced options (hints, ID, epoch) remain collapsed and empty.
- **Action**: Click **"Investigate Incident"**.
- **Visual**: The app automatically navigates to Step 2 (**Result**). Focus shifts to the findings header.
- **Narration**:
  > "Notice that we provided zero manual hints. Our entry point resolver analyzes the raw alert text against the FalkorDB catalog and resolves `checkout-service` as the symptom entry point and `err-sig-payment-pool-timeout` as the matched error signature."

---

## Scene 3: Explainable Root-Cause Ranking & Graph Path (1:30 – 2:30)
- **Visual**: On Step 2 (**Result**), point out the top **Most Likely Cause** lead card:
  - Top suspect: `#1 payment-service` (**STRONG TIER**)
  - Plain English explanation: *"Recent deployment change on this service AND historical match for this error signature."*
  - Recent change: `dep-payment-v2.4.1` (30m before incident)
  - Seen before: past incident with confirmed fix
- **Action**: Expand/highlight the open accordions:
  - **Why we think so**: Deployments in window and historical incident match.
  - **Dependency chain & blast radius**: Visual box-and-arrow call path showing `service-checkout ──calls──► service-order ──calls──► service-payment` (2 calls away from failing service).
- **Action**: Click **"Confirm Root Cause & Fix ➔"** (or click tab 3 **Confirm fix**).
- **Narration**:
  > "DevTrace AI evaluates the topological neighborhood downstream from checkout. 
  > Notice the ranking: `payment-service` deployed 30 minutes ago and matched a historical incident with connection pool timeouts, placing it as our #1 STRONG candidate.
  > FalkorDB traces the exact 2-hop directed dependency path: checkout calls order, which calls payment. The failure propagated upstream in reverse."

---

## Scene 4: Closed-Loop Human Confirmation & Resolution Write-Back (2:30 – 3:15)
- **Visual**: Step 3 (**Confirm fix**) is active. Heading states: *"Confirm the fix (human review)"* with *"You are confirming a fix for incident inc-2026-10-15-checkout-001"*.
- **Action**: Click the **"Fill demo resolution (DEMO)"** button.
- **Visual**: The resolution fields populate:
  - **Which service was the real cause?**: `service-payment` (selected from dropdown)
  - **What kind of fix?**: `CONFIG_CHANGE`
  - **Your name or team**: `sre-oncall-lead`
  - **Short title**: `Emergency pool size rollback and idle timeout tune on payment datasource`
  - **What exactly was done?**: `Restored HikariCP maximumPoolSize from 10 back to 50, raised connectionTimeout to 30000ms, and verified connection wait queue dropped to zero under load.`
  - **Related deployment**: blank
- **Action**: Paste your `DEVTRACE_API_TOKEN` (copied previously to clipboard) into the masked **API token** field (never open `.env` on camera; point out the 5-step collapsible helper if desired).
- **Action**: Click **"Save confirmed fix"**.
- **Visual**: Focus shifts to the green confirmation banner:
  - `✔ Resolution Confirmed & Written to FalkorDB` showing resolution ID `res-inc-2026-10-15-checkout-001` with `source: user-confirmed`.
  - Explains what changed in the knowledge graph: new `Resolution` node, linked via `[:RESOLVES]` and `[:ADDRESSES]`.
- **Narration**:
  > "Investigation engines should never persist speculative hypotheses. DevTrace AI requires an authenticated human SRE to confirm the actual fix.
  > Clicking 'Save confirmed fix' executes an atomic Cypher write-back transaction in FalkorDB. It marks incident A RESOLVED, links the resolution to the payment service, and tags it with 'user-confirmed' provenance."

---

## Scene 5: Institutional Memory in Action — Investigating Incident B (3:15 – 3:45)
- **Action**: Click the **"Next: try similar incident B"** button directly on the confirmation banner.
- **Visual**: The app returns to Step 1 (**Describe**) with incident B pre-filled:
  - **Incident ID / Title**: `inc-2026-10-15-checkout-002`
  - **What is going wrong?**: `Checkout failure rates spiking again with persistent payment timeouts on card transactions`
  - **Started At**: `1792065600000` (Readable UTC: `2026-10-15T12:00:00.000Z`)
- **Action**: Click **"Investigate Incident"**.
- **Visual**: On Step 2 (**Result**), open "Why we think so" -> Matched Historical Incidents. Observe that **both** resolutions are now returned in recency order:
  1. `inc-2026-10-15-checkout-001` with resolution `res-inc-2026-10-15-checkout-001` (`source: user-confirmed`): *"Emergency pool size rollback and idle timeout tune on payment datasource"*
  2. `inc-2026-08-12-pay` with resolution `res-pay-timeout-fix` (`source: synthetic`): *"Increase HikariCP pool size and connection timeout"*
- **Narration**:
  > "When a subsequent incident occurs, DevTrace AI's connected graph instantly surfaces the newly confirmed resolution as institutional memory. 
  > Notice also that Incident A never appears in its own history, preserving strict causal isolation.
  > That is closed-loop operational intelligence with FalkorDB."

---

## Post-Demo / Post-Recording Steps
- [ ] **Clear clipboard history**: If desired, clear your system clipboard history (e.g. `Win + V` -> "Clear all" on Windows, or copy a neutral string) so the token value does not linger in memory or clipboard managers.
- [ ] **Reset database**: If recording multiple takes, re-run the database reset step so each take starts from the clean 24-node golden seed state.

