# DevTrace AI — Candidate Cause Ranking Specification

## 1. Ranking Philosophy

DevTrace AI deliberately rejects opaque weighted scoring formulas and artificial "confidence percentages" (such as claiming "87% confidence"). In operational production incidents, numeric scores provide a false sense of precision without statistical calibration.

Instead, DevTrace AI employs **deterministic rule-based evidence tiers**:
- Hypotheses are categorized into discrete, verifiable evidentiary tiers based on graph connectivity.
- Within each tier, candidates are ordered by fully deterministic tie-breaking rules.
- If no candidate satisfies the threshold for at least `MODERATE` confidence, the engine transitions to `INSUFFICIENT_EVIDENCE`, refuses to speculate, and explicitly declares what evidence is missing.

---

## 2. Rule-Based Tiers

| Tier | Definition | Evidentiary Requirements | Example in Golden Scenario |
| :--- | :--- | :--- | :--- |
| **`STRONG`** | Verified Multi-Factor Convergence | **Both** a recent deployment on the service within the window **AND** historical incident affinity matching the error signature. | `payment-service` (has `dep-payment-v2.4.1` deployed 30m prior + historical incident `inc-2026-08-12-pay` matching `err-sig-payment-pool-timeout`). |
| **`MODERATE`** | Single-Factor Evidence | **Either** a recent deployment only (no error signature match) **OR** historical signature affinity only (no recent deployments). | `inventory-service` (has recent deployment `dep-inventory-v1.5.0` 10m prior, but zero error signature match). |
| **`WEAK`** | Topological Reachability Only | Reachable within downstream dependency neighborhood, but has **neither** recent deployments **nor** historical signature affinity. | `order-service`, `postgres-orders`, `redis-cache` (reachable in call chain, but no changes or matches). |

---

## 3. Deterministic Sorting Rules

Candidates are sorted across and within tiers according to a strict priority hierarchy:

1. **Tier Precedence**: `STRONG` candidates always rank ahead of `MODERATE` candidates, which always rank ahead of `WEAK` candidates.
2. **Change Recency (Within Tier)**: Candidates with more recent deployments rank ahead of older deployments (`mostRecentChangeTimestamp` descending, nulls last).
3. **Hop Proximity (Within Tier & Recency)**: Candidates topologically closer to the symptom service rank ahead of more distant candidates (`hopDistance` ascending).
4. **Alphanumeric Tie-Break**: If tier, recency, and hop distance are identical, candidates are ordered deterministically by unique `serviceId` ascending.

> [!IMPORTANT]
> **Proximity is a tie-break only and never outranks evidence.**
> In Act 1, `inventory-service` is topologically closer (1 hop from checkout) and was deployed more recently (10m ago) than `payment-service` (2 hops, 30m ago). Under naive proximity or recency heuristics, inventory would win. In DevTrace AI, `payment-service` achieves the `STRONG` tier due to error signature affinity, correctly placing it at Rank 1.

---

## 4. Candidate Neighborhood & Out-of-Scope Changes

- **Neighborhood Scoping**: Candidates are strictly bounded to the symptom service and services reachable downstream within `maxDownstreamDepth`.
- **Ignored Changes**: Infrastructure changes or deployments occurring outside this neighborhood (such as `dep-notification-v1.8.0` on `notification-service`) are **never ranked as candidates**.
- The engine partitions these into an explicit `ignoredChanges` list with the reason:
  `"recent changes outside the dependency neighborhood, not considered"`

---

## 5. Insufficient Evidence Mode

If no candidate in the neighborhood achieves `MODERATE` or `STRONG` status:
- The investigation status is set to **`INSUFFICIENT_EVIDENCE`**.
- The engine **refuses to name a suspected root cause**.
- The report prominently populates `observedFacts.missingEvidence` with specific missing items:
  1. `"no matching signature resolved from request"` (if no error pattern matched).
  2. `"no deployments in the <window>h window on candidate services"` (if no recent changes occurred).
  3. `"no similar historical incident matching signature on candidate services"` (if no past incidents match).

---

## 6. Configurable Parameters

| Configuration Property | Default Value | Description |
| :--- | :--- | :--- |
| `devtrace.investigation.max-depth` | `3` | Maximum downstream dependency hops traversed from symptom service. |
| `devtrace.investigation.window-ms` | `86400000` (24h) | Temporal lookback window before incident `startedAt` for deployment correlation. |

---

## 7. Limitations & Honest Assessment

- **Uncalibrated Heuristic**: DevTrace AI is a rule-based deterministic heuristic designed for structured graph reasoning. It is **not** a calibrated probabilistic machine learning model and makes no claims of percentage accuracy.
- **Topological Incompleteness**: If a service dependency is not captured in the FalkorDB graph, or if telemetry failed to capture a deployment event, the engine cannot infer it.
- **Human-in-the-Loop Requirement**: All hypotheses and suggested remediations are advisory and must be verified by on-call SREs prior to taking destructive production action.
