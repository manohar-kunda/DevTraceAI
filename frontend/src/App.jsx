import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { useHashRoute } from './hooks/useHashRoute.js';
import { useInvestigation } from './hooks/useInvestigation.js';
import { getGoldenIncident, confirmResolution, getHealth, getIncident } from './api/endpoints.js';
import { buildResolutionPayload, buildIncidentPayload } from './api/payloads.js';
import { DEMO_INCIDENT_B, DEMO_RESOLUTION_A } from './demo/demoData.js';
import { extractCandidateServices } from './utils/format.js';
import { ErrorBanner } from './components/common/ErrorBanner.jsx';
import { DescribePage } from './pages/DescribePage.jsx';
import { ResultPage } from './pages/ResultPage.jsx';
import { ConfirmPage } from './pages/ConfirmPage.jsx';

const INIT_INC = { description: '', serviceHint: '', errorHint: '', startedAt: '', incidentId: '' };
const INIT_RES = { rootCauseServiceId: '', triggeringDeploymentId: '', title: '', resolutionTitle: '', remediationType: 'CONFIG_CHANGE', actionTaken: '', confirmedBy: '' };

export default function App() {
  const [hash, navigate] = useHashRoute('#/describe');
  const inv = useInvestigation();
  const [incForm, setIncForm] = useState(INIT_INC);
  const [resForm, setResForm] = useState(INIT_RES);
  const [token, setToken] = useState(''); // React state only; never in storage/URL/logs
  const [resolutionResult, setResolutionResult] = useState(null);
  const [resolving, setResolving] = useState(false);
  const [isDemoMode, setIsDemoMode] = useState(false);
  const [backendHealth, setBackendHealth] = useState({ checked: false, connected: false });

  const setIncField = useCallback((k, v) => setIncForm((p) => ({ ...p, [k]: v })), []);
  const setResField = useCallback((k, v) => setResForm((p) => ({ ...p, [k]: v })), []);

  useEffect(() => {
    getHealth().then((r) => setBackendHealth({ checked: true, connected: r?.status === 'UP' })).catch(() => setBackendHealth({ checked: true, connected: false }));
  }, []);

  const handleLoadDemoA = useCallback(async () => {
    inv.clearError(); setIsDemoMode(true);
    try {
      const d = await getGoldenIncident();
      setIncForm({ description: d.description || '', serviceHint: d.serviceHint || '', errorHint: d.errorHint || '', startedAt: d.startedAt ? String(d.startedAt) : '', incidentId: d.incidentId || '' });
    } catch (e) {
      inv.setError({ title: 'Demo Load Failed', detail: e.detail || e.message, status: 500 });
    }
  }, [inv]);

  const handleLoadDemoB = useCallback(() => {
    inv.clearError(); setIsDemoMode(true); setIncForm(DEMO_INCIDENT_B); navigate('#/describe');
  }, [inv, navigate]);

  const handleInvestigateSubmit = async (e) => {
    e.preventDefault(); setResolutionResult(null);
    const res = await inv.runInvestigation(buildIncidentPayload(incForm));
    if (res) {
      const top = res.report?.hypotheses?.[0]?.serviceId || res.report?.otherServicesInNeighborhood?.[0]?.id || '';
      setResForm({ ...INIT_RES, rootCauseServiceId: top });
      navigate('#/result');
    }
  };

  const handleConfirmResolution = async (e) => {
    e.preventDefault();
    const activeId = incForm.incidentId.trim() || inv.currentIncident?.incidentId;
    if (!token.trim()) {
      inv.setError({ title: 'Authentication Required', detail: 'Enter the API token before confirming.', status: 401 });
      return;
    }
    setResolving(true); inv.clearError();
    try {
      const payload = buildResolutionPayload(resForm);
      const resData = await confirmResolution(activeId, payload, token);
      setResolutionResult(resData);
      if (activeId) getIncident(activeId).then((r) => inv.setCurrentIncident(r)).catch(() => {});
    } catch (err) {
      inv.setError({
        title: err.status === 401 ? 'Unauthorized (401)' : (err.title || 'Write-back Failed'),
        detail: err.status === 401 ? "The server rejected the token. It must exactly match DEVTRACE_API_TOKEN in the backend's .env, and the backend must be restarted after .env changes." : (err.detail || 'Could not confirm resolution.'),
        status: err.status || 500, correlationId: err.correlationId, fieldErrors: err.fieldErrors,
      });
    } finally {
      setResolving(false);
    }
  };

  const candidateServices = useMemo(() => extractCandidateServices(inv.report), [inv.report]);
  const currentDemoStep = useMemo(() => {
    if (hash === '#/result') return 2;
    if (hash === '#/confirm') return resolutionResult ? 4 : 3;
    if (incForm.incidentId === DEMO_INCIDENT_B.incidentId) return 4;
    return 1;
  }, [hash, resolutionResult, incForm.incidentId]);

  return (
    <div className="app-container">
      <a href="#main-content" className="skip-link">Skip to main content</a>
      <header className="app-header">
        <div>
          <h1 className="brand-title">DevTrace AI</h1>
          <p className="brand-subtitle">Graph-Native Incident Investigation & Remediation Knowledge Graph</p>
        </div>
        <div className="header-badges">
          <span className="demo-pill" title="Pre-seeded microservice topology in FalkorDB">● Synthetic Demo Data</span>
          {backendHealth.checked && (
            <span className={`health-pill ${backendHealth.connected ? 'health-online' : 'health-offline'}`}>
              Backend: {backendHealth.connected ? 'connected' : 'not reachable'}
            </span>
          )}
        </div>
      </header>

      <ErrorBanner error={inv.error} onDismiss={inv.clearError} />

      <nav className="stepper-nav" aria-label="Investigation progress steps">
        <button type="button" className={`step-tab ${hash === '#/describe' ? 'active' : ''}`} onClick={() => navigate('#/describe')}>
          <span className="step-number">1</span> Describe
        </button>
        <button type="button" className={`step-tab ${hash === '#/result' ? 'active' : ''}`} disabled={!inv.report} onClick={() => navigate('#/result')}>
          <span className="step-number">2</span> Result
        </button>
        <button type="button" className={`step-tab ${hash === '#/confirm' ? 'active' : ''}`} disabled={!inv.report} onClick={() => navigate('#/confirm')}>
          <span className="step-number">3</span> Confirm fix
        </button>
      </nav>

      <main id="main-content">
        {hash === '#/describe' && (
          <DescribePage
            form={incForm} setField={setIncField} onSubmit={handleInvestigateSubmit}
            onLoadDemoA={handleLoadDemoA} onLoadDemoB={handleLoadDemoB} loading={inv.isLoading}
            isDemoMode={isDemoMode} demoStep={currentDemoStep} onNextDemoStep={() => navigate('#/result')}
          />
        )}
        {hash === '#/result' && (
          <ResultPage
            report={inv.report} incidentId={incForm.incidentId || inv.currentIncident?.incidentId}
            onProceedToConfirm={() => navigate('#/confirm')} isDemoMode={isDemoMode} demoStep={currentDemoStep}
            onNextDemoStep={() => navigate('#/confirm')}
          />
        )}
        {hash === '#/confirm' && (
          <ConfirmPage
            incidentId={incForm.incidentId || inv.currentIncident?.incidentId} candidateServices={candidateServices}
            form={resForm} setField={setResField} token={token} setToken={setToken}
            onFillDemo={() => setResForm({ ...DEMO_RESOLUTION_A, title: DEMO_RESOLUTION_A.resolutionTitle })}
            onSubmit={handleConfirmResolution} resolving={resolving} resolutionResult={resolutionResult}
            onTryDemoB={handleLoadDemoB} isDemoMode={isDemoMode} demoStep={currentDemoStep} onNextDemoStep={handleLoadDemoB}
          />
        )}
      </main>
    </div>
  );
}
