import { useState, useEffect } from 'react';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8085';

export default function App() {
  // Incident Form state
  const [description, setDescription] = useState('');
  const [serviceHint, setServiceHint] = useState('');
  const [errorHint, setErrorHint] = useState('');
  const [startedAt, setStartedAt] = useState('');
  const [incidentId, setIncidentId] = useState('');

  // Execution state
  const [loading, setLoading] = useState(false);
  const [report, setReport] = useState(null);
  const [error, setError] = useState(null);

  // Active Incident state
  const [currentIncident, setCurrentIncident] = useState(null);

  // Resolution Form state
  const [rootCauseServiceId, setRootCauseServiceId] = useState('');
  const [triggeringDeploymentId, setTriggeringDeploymentId] = useState('');
  const [resolutionTitle, setResolutionTitle] = useState('');
  const [remediationType, setRemediationType] = useState('CONFIG_CHANGE');
  const [actionTaken, setActionTaken] = useState('');
  const [confirmedBy, setConfirmedBy] = useState('');
  const [token, setToken] = useState(''); // in-memory only; never written to localStorage or logs
  const [resolutionResult, setResolutionResult] = useState(null);
  const [resolving, setResolving] = useState(false);

  // Load Golden Demo Incident
  const handleLoadDemo = async () => {
    setError(null);
    try {
      const res = await fetch(`${API_BASE}/api/demo/golden-incident`);
      if (!res.ok) {
        const errJson = await res.json().catch(() => ({}));
        throw new Error(errJson.detail || `HTTP ${res.status}`);
      }
      const data = await res.json();
      setDescription(data.description || '');
      setServiceHint(data.serviceHint || '');
      setErrorHint(data.errorHint || '');
      setStartedAt(data.startedAt ? String(data.startedAt) : '');
      setIncidentId(data.incidentId || '');
    } catch (e) {
      setError({
        title: 'Failed to load demo scenario',
        detail: e.message,
        status: 500,
      });
    }
  };

  // Submit Incident & Investigate
  const handleInvestigate = async (e) => {
    e.preventDefault();
    if (!description.trim()) {
      setError({
        title: 'Validation Error',
        detail: 'Description is required to initiate investigation.',
        status: 400,
      });
      return;
    }

    setLoading(true);
    setError(null);
    setReport(null);
    setResolutionResult(null);

    try {
      // 1. Create or ensure Incident node
      const targetId = incidentId.trim() || `inc-${Date.now()}`;
      const createPayload = {
        description: description.trim(),
        serviceHint: serviceHint.trim() || null,
        errorHint: errorHint.trim() || null,
        startedAt: startedAt ? Number(startedAt) : null,
        incidentId: targetId,
      };

      const createRes = await fetch(`${API_BASE}/api/incidents`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(createPayload),
      });

      const createData = await createRes.json();
      if (!createRes.ok && createRes.status !== 200 && createRes.status !== 201) {
        throw createData;
      }

      setCurrentIncident(createData);

      // 2. Perform Investigation
      const invPayload = {
        description: description.trim(),
        serviceHint: serviceHint.trim() || null,
        errorHint: errorHint.trim() || null,
        startedAt: startedAt ? Number(startedAt) : null,
        incidentId: targetId,
      };

      const invRes = await fetch(`${API_BASE}/api/incidents/${targetId}/investigation`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(invPayload),
      });

      const invData = await invRes.json();
      if (!invRes.ok) {
        throw invData;
      }

      setReport(invData);

      // Pre-fill resolution form defaults based on top hypothesis if available
      if (invData.hypotheses && invData.hypotheses.length > 0) {
        const top = invData.hypotheses[0];
        setRootCauseServiceId(top.serviceId || '');
        if (top.confirmedResolution) {
          setResolutionTitle(top.confirmedResolution.title || '');
          setRemediationType(top.confirmedResolution.remediationType || 'CONFIG_CHANGE');
          setActionTaken(top.confirmedResolution.actionTaken || '');
        } else {
          setResolutionTitle(`Remediate ${top.serviceName || top.serviceId}`);
          setActionTaken('');
        }
      }
      setConfirmedBy('sre-oncall');
    } catch (err) {
      setError({
        title: err.title || 'Error',
        detail: err.detail || err.message || 'An unexpected error occurred during investigation.',
        status: err.status || 500,
        correlationId: err.correlationId,
      });
    } finally {
      setLoading(false);
    }
  };

  // Confirm Resolution
  const handleConfirmResolution = async (e) => {
    e.preventDefault();
    if (!currentIncident || !currentIncident.incidentId) {
      setError({
        title: 'Precondition Failed',
        detail: 'An active incident must be loaded before confirming a resolution.',
        status: 400,
      });
      return;
    }

    if (!token.trim()) {
      setError({
        title: 'Unauthorized',
        detail: 'DevTrace API token is required to execute confirmation write-back.',
        status: 401,
      });
      return;
    }

    setResolving(true);
    setError(null);

    try {
      const resPayload = {
        rootCauseServiceId: rootCauseServiceId.trim(),
        title: resolutionTitle.trim(),
        remediationType: remediationType.trim(),
        actionTaken: actionTaken.trim(),
        confirmedBy: confirmedBy.trim(),
        triggeringDeploymentId: triggeringDeploymentId.trim() || null,
      };

      const res = await fetch(`${API_BASE}/api/incidents/${currentIncident.incidentId}/resolution`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'X-DevTrace-Token': token.trim(),
        },
        body: JSON.stringify(resPayload),
      });

      const resData = await res.json();
      if (!res.ok) {
        throw resData;
      }

      setResolutionResult(resData);
      // Refresh incident state
      const refreshed = await fetch(`${API_BASE}/api/incidents/${currentIncident.incidentId}`);
      if (refreshed.ok) {
        const incData = await refreshed.json();
        setCurrentIncident(incData);
      }
    } catch (err) {
      setError({
        title: err.title || 'Write-back Failed',
        detail: err.detail || err.message || 'Could not confirm resolution.',
        status: err.status || 500,
        correlationId: err.correlationId,
      });
    } finally {
      setResolving(false);
    }
  };

  return (
    <div className="app-container">
      {/* Header */}
      <header className="app-header">
        <div>
          <h1 className="brand-title">DevTrace AI</h1>
          <p className="brand-subtitle">Graph-Native Incident Investigation & Remediation Knowledge Graph</p>
        </div>
        <div>
          <span className="demo-pill" title="Pre-seeded microservice topology in FalkorDB">
            ● Synthetic Demo Data
          </span>
        </div>
      </header>

      {/* RFC 7807 Error Banner */}
      {error && (
        <div className="error-banner" role="alert">
          <div className="error-banner-header">
            <span>{error.title} {error.status ? `(${error.status})` : ''}</span>
            <button
              onClick={() => setError(null)}
              style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'inherit', fontWeight: 'bold' }}
            >
              ✕
            </button>
          </div>
          <p className="error-banner-detail">{error.detail}</p>
          {error.correlationId && (
            <p className="error-banner-meta">Correlation ID: {error.correlationId}</p>
          )}
        </div>
      )}

      {/* Incident Input Form */}
      <section className="card">
        <h2 className="card-title">1. Initiate Incident Investigation</h2>
        <form onSubmit={handleInvestigate}>
          <div className="form-grid">
            <div className="form-group">
              <label htmlFor="description">Incident Description / SRE Alert *</label>
              <textarea
                id="description"
                rows="3"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="e.g. Checkout requests are failing after the latest deployment and payment timeouts have increased"
                required
              />
            </div>

            <div className="form-row-hints">
              <div className="form-group">
                <label htmlFor="serviceHint">Symptom Service Hint (Optional)</label>
                <input
                  id="serviceHint"
                  type="text"
                  value={serviceHint}
                  onChange={(e) => setServiceHint(e.target.value)}
                  placeholder="e.g. checkout-service"
                />
              </div>

              <div className="form-group">
                <label htmlFor="errorHint">Error Signature Hint (Optional)</label>
                <input
                  id="errorHint"
                  type="text"
                  value={errorHint}
                  onChange={(e) => setErrorHint(e.target.value)}
                  placeholder="e.g. err-sig-payment-pool-timeout"
                />
              </div>

              <div className="form-group">
                <label htmlFor="incidentId">Incident ID (Optional)</label>
                <input
                  id="incidentId"
                  type="text"
                  value={incidentId}
                  onChange={(e) => setIncidentId(e.target.value)}
                  placeholder="e.g. inc-2026-10-15-checkout-001"
                />
              </div>
            </div>

            <div className="form-group" style={{ maxWidth: '300px' }}>
              <label htmlFor="startedAt">Started At Epoch ms (Optional)</label>
              <input
                id="startedAt"
                type="number"
                value={startedAt}
                onChange={(e) => setStartedAt(e.target.value)}
                placeholder="1792058400000"
              />
            </div>
          </div>

          <div className="form-actions">
            <button
              type="button"
              className="btn btn-secondary"
              onClick={handleLoadDemo}
              disabled={loading}
            >
              Load Demo Golden Incident
            </button>
            <button
              type="submit"
              className="btn btn-primary"
              disabled={loading}
            >
              {loading ? 'Investigating Topology...' : 'Investigate Incident'}
            </button>
          </div>
        </form>
      </section>

      {/* Structured Investigation Report */}
      {report && (
        <section className="card">
          <div className="report-header">
            <div>
              <h2 style={{ fontSize: '18px', fontWeight: 700 }}>Investigation Report</h2>
              <p style={{ fontSize: '13px', color: '#64748b' }}>
                Incident ID: {report.requestEcho?.incidentId || currentIncident?.incidentId}
              </p>
            </div>
            <div>
              <span className={`status-badge ${
                report.status === 'HYPOTHESES_RANKED' ? 'status-ranked' : 'status-insufficient'
              }`}>
                {report.status}
              </span>
            </div>
          </div>

          {/* Section A: Entry Points */}
          <h3 className="section-title">A. Resolved Entry Points</h3>
          <div className="kv-grid">
            <div className="kv-item">
              <span className="kv-label">Symptom Service</span>
              <span className="kv-value">
                {report.entryPoints?.service?.serviceName} ({report.entryPoints?.service?.serviceId})
              </span>
              <span style={{ fontSize: '11px', color: '#64748b', marginTop: '4px' }}>
                {report.entryPoints?.service?.evidence?.explanation}
              </span>
            </div>
            <div className="kv-item">
              <span className="kv-label">Matched Error Signature</span>
              <span className="kv-value">
                {report.entryPoints?.errorSignature?.errorType} ({report.entryPoints?.errorSignature?.signatureId})
              </span>
              <span style={{ fontSize: '11px', color: '#64748b', marginTop: '4px' }}>
                {report.entryPoints?.errorSignature?.pattern}
              </span>
            </div>
          </div>

          {/* Section B: Ranked Hypotheses */}
          <h3 className="section-title">B. Ranked Root-Cause Hypotheses</h3>
          {report.hypotheses && report.hypotheses.length > 0 ? (
            <div>
              {report.hypotheses.map((h) => (
                <div key={h.serviceId} className="hypothesis-card">
                  <div className="hypothesis-header">
                    <span style={{ fontWeight: 700, fontSize: '15px' }}>
                      #{h.rank} {h.serviceName} ({h.serviceId})
                    </span>
                    <span className={`tier-badge ${h.tier === 'STRONG' ? 'tier-strong' : 'tier-moderate'}`}>
                      {h.tier} TIER
                    </span>
                  </div>

                  <p style={{ fontSize: '14px', color: '#334155', marginBottom: '8px' }}>
                    {h.plainLanguageReason}
                  </p>

                  <div style={{ fontSize: '12px', color: '#64748b', marginBottom: '8px' }}>
                    Topology Distance: <strong>{h.hopDistance} hops</strong> | Most Recent Change: <strong>{new Date(h.mostRecentChangeTimestamp).toISOString()}</strong>
                  </div>

                  {h.evidence && h.evidence.length > 0 && (
                    <ul className="evidence-list">
                      {h.evidence.map((ev, idx) => (
                        <li key={idx} className="evidence-item">
                          <strong>[{ev.evidenceType}]</strong> {ev.description}
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <p style={{ fontSize: '14px', color: '#64748b' }}>No strong or moderate hypotheses identified.</p>
          )}

          {/* Section C: Topological Failure Propagation Path */}
          <h3 className="section-title">C. Failure Propagation Path & Blast Radius</h3>
          {report.graphRelationships?.pathsToCandidates && (
            <div>
              <p style={{ fontSize: '13px', color: '#475569' }}>
                Directed dependency paths from symptom caller down to candidate root causes (failure propagates in reverse):
              </p>
              {Object.entries(report.graphRelationships.pathsToCandidates).map(([svc, p]) => (
                <div key={svc} className="path-box">
                  {p.serviceIds.join(' ──[:DEPENDS_ON]──► ')} ({p.hopCount} hops)
                </div>
              ))}
            </div>
          )}

          {report.graphRelationships?.blastRadius && (
            <div style={{ marginTop: '14px' }}>
              <span className="kv-label">Blast Radius for {report.graphRelationships.blastRadius.targetServiceId}:</span>
              <div className="tag-group">
                {report.graphRelationships.blastRadius.directDependents?.map((d) => (
                  <span key={d.serviceId} className="tag" style={{ borderLeft: '3px solid #2563eb' }}>
                    {d.serviceName} (1 hop)
                  </span>
                ))}
                {report.graphRelationships.blastRadius.indirectDependents?.map((d) => (
                  <span key={d.serviceId} className="tag" style={{ borderLeft: '3px solid #94a3b8' }}>
                    {d.serviceName} ({d.hops} hops)
                  </span>
                ))}
              </div>
            </div>
          )}

          {/* Section D: Suggested Next Steps */}
          <h3 className="section-title">D. Suggested Next Steps</h3>
          {report.suggestedNextSteps && report.suggestedNextSteps.length > 0 ? (
            <div>
              {report.suggestedNextSteps.map((step, idx) => (
                <div key={idx} className="step-item">
                  <div className="step-label">
                    {idx + 1}. {step.label}
                  </div>
                  <div className="step-desc">{step.description}</div>
                </div>
              ))}
            </div>
          ) : (
            <p style={{ fontSize: '14px', color: '#64748b' }}>No immediate steps suggested.</p>
          )}

          {/* Section E: Ignored Changes Outside Neighborhood */}
          {report.ignoredChanges && report.ignoredChanges.length > 0 && (
            <div>
              <h3 className="section-title">E. Ignored Changes Outside Dependency Neighborhood</h3>
              <ul className="evidence-list">
                {report.ignoredChanges.map((c) => (
                  <li key={c.changeId} className="evidence-item" style={{ borderLeftColor: '#94a3b8' }}>
                    <strong>{c.changeId}</strong> on <em>{c.serviceName}</em> ({c.changeType} at {new Date(c.timestamp).toISOString()}): {c.reason}
                  </li>
                ))}
              </ul>
            </div>
          )}

          {/* Section F: Limitations & Assumptions */}
          <h3 className="section-title">F. Reasoning Boundaries & Limitations</h3>
          <ul className="limitation-list">
            {report.limitations?.map((lim, idx) => (
              <li key={idx}>{lim}</li>
            ))}
          </ul>
        </section>
      )}

      {/* Confirmation & Write-Back Form */}
      {report && (
        <section className="card">
          <h2 className="card-title">2. Human-in-the-Loop Resolution Write-Back</h2>
          <p style={{ fontSize: '13px', color: '#64748b', marginBottom: '16px' }}>
            Confirming this resolution atomically writes a <code>Resolution</code> node with <code>source='user-confirmed'</code> into FalkorDB and links it via <code>[:RESOLVES]</code> and <code>[:ADDRESSES]</code>, evolving the knowledge graph for future investigations.
          </p>

          <form onSubmit={handleConfirmResolution}>
            <div className="form-grid">
              <div className="form-row-hints">
                <div className="form-group">
                  <label htmlFor="rootCauseServiceId">Root Cause Service ID *</label>
                  <input
                    id="rootCauseServiceId"
                    type="text"
                    value={rootCauseServiceId}
                    onChange={(e) => setRootCauseServiceId(e.target.value)}
                    required
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="remediationType">Remediation Type *</label>
                  <select
                    id="remediationType"
                    value={remediationType}
                    onChange={(e) => setRemediationType(e.target.value)}
                    required
                  >
                    <option value="CONFIG_CHANGE">CONFIG_CHANGE</option>
                    <option value="ROLLBACK">ROLLBACK</option>
                    <option value="RESTART">RESTART</option>
                    <option value="HOTFIX">HOTFIX</option>
                    <option value="SCALE">SCALE</option>
                  </select>
                </div>

                <div className="form-group">
                  <label htmlFor="confirmedBy">Confirmed By *</label>
                  <input
                    id="confirmedBy"
                    type="text"
                    value={confirmedBy}
                    onChange={(e) => setConfirmedBy(e.target.value)}
                    required
                  />
                </div>
              </div>

              <div className="form-group">
                <label htmlFor="resolutionTitle">Resolution Title *</label>
                <input
                  id="resolutionTitle"
                  type="text"
                  value={resolutionTitle}
                  onChange={(e) => setResolutionTitle(e.target.value)}
                  placeholder="e.g. Reverted HikariCP connection pool constraints via hotfix config deploy"
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="actionTaken">Action Taken (Detailed Remediation) *</label>
                <textarea
                  id="actionTaken"
                  rows="2"
                  value={actionTaken}
                  onChange={(e) => setActionTaken(e.target.value)}
                  placeholder="Detailed notes of configuration changes, pool adjustments, or rollback commands executed"
                  required
                />
              </div>

              <div className="form-row-hints">
                <div className="form-group">
                  <label htmlFor="triggeringDeploymentId">Triggering Deployment ID (Optional)</label>
                  <input
                    id="triggeringDeploymentId"
                    type="text"
                    value={triggeringDeploymentId}
                    onChange={(e) => setTriggeringDeploymentId(e.target.value)}
                    placeholder="e.g. dep-payment-v2.4.1"
                  />
                </div>

                <div className="form-group">
                  <label htmlFor="token">DevTrace API Token (In-Memory Only) *</label>
                  <input
                    id="token"
                    type="password"
                    value={token}
                    onChange={(e) => setToken(e.target.value)}
                    placeholder="Enter DEVTRACE_API_TOKEN"
                    required
                  />
                </div>
              </div>
            </div>

            <div className="form-actions">
              <button
                type="submit"
                className="btn btn-success"
                disabled={resolving}
              >
                {resolving ? 'Writing back to FalkorDB...' : 'Confirm Resolution & Evolve Graph'}
              </button>
            </div>
          </form>

          {/* Resolution Result Banner */}
          {resolutionResult && (
            <div className="confirmed-alert">
              <strong>✔ Resolution Confirmed & Written to FalkorDB:</strong>
              <div style={{ marginTop: '8px', fontSize: '13px' }}>
                <div>Resolution ID: <code>{resolutionResult.id}</code></div>
                <div>Source: <code>{resolutionResult.source}</code></div>
                <div>Confirmed At: <code>{new Date(resolutionResult.confirmedAt).toISOString()}</code></div>
                <div>Confirmed By: <code>{resolutionResult.confirmedBy}</code></div>
              </div>
            </div>
          )}
        </section>
      )}
    </div>
  );
}
