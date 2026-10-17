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

## Scene 4: Closed-Loop Human Confirmation & Graph Evolution (2:30 – 3:30)
- **Action**: Scroll down to **"2. Human-in-the-Loop Resolution Write-Back"**.
- **Action**: Point out that the form auto-populated the recommended root cause (`service-payment`) and remediation notes. Enter the `DEVTRACE_API_TOKEN` in the password field.
- **Action**: Click **"Confirm Resolution & Evolve Graph"**.
- **Visual**: The green confirmation banner appears: `✔ Resolution Confirmed & Written to FalkorDB` showing resolution ID `res-inc-2026-10-15-checkout-001` with `source: user-confirmed`.
- **Narration**:
  > "Investigation engines should never persist speculative hypotheses. DevTrace AI requires an authenticated human SRE to confirm the actual fix.
  > Clicking Confirm executes an atomic Cypher write-back transaction in FalkorDB. It marks the incident RESOLVED, links the resolution to the payment service, and tags it with 'user-confirmed' provenance.
  > On future incidents exhibiting this error signature, this remediation will immediately surface as verified institutional knowledge.
  > That is closed-loop operational intelligence with FalkorDB."
