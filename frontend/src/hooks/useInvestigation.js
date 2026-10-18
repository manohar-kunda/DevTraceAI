import { useState, useCallback, useRef } from 'react';
import { createIncident, investigate } from '../api/endpoints.js';

export function useInvestigation() {
  const [status, setStatus] = useState('idle'); // 'idle' | 'loading' | 'success' | 'error'
  const [report, setReport] = useState(null);
  const [generatedAt, setGeneratedAt] = useState(null);
  const [currentIncident, setCurrentIncident] = useState(null);
  const [error, setError] = useState(null);
  const lastRequestIdRef = useRef(0);

  const runInvestigation = useCallback(async (formValues) => {
    const { description, serviceHint, errorHint, startedAt, incidentId } = formValues;
    if (!description || !description.trim()) {
      setError({
        title: 'Validation Error',
        detail: 'Description is required to initiate investigation.',
        status: 400,
      });
      setStatus('error');
      return null;
    }

    const currentReqId = ++lastRequestIdRef.current;
    setStatus('loading');
    setError(null);
    setReport(null);

    const targetId = incidentId?.trim() || `inc-${Date.now()}`;
    const payload = {
      description: description.trim(),
      serviceHint: serviceHint?.trim() || null,
      errorHint: errorHint?.trim() || null,
      startedAt: startedAt ? Number(startedAt) : null,
      incidentId: targetId,
    };

    try {
      // 1. Create / ensure incident
      const inc = await createIncident(payload);
      if (currentReqId !== lastRequestIdRef.current) return null; // Stale request check
      setCurrentIncident(inc);

      // 2. Perform investigation
      const rep = await investigate(targetId, payload);
      if (currentReqId !== lastRequestIdRef.current) return null; // Stale request check

      setReport(rep);
      setGeneratedAt(new Date().toISOString());
      setStatus('success');
      return { incident: inc, report: rep };
    } catch (err) {
      if (currentReqId !== lastRequestIdRef.current) return null; // Ignore stale errors
      setError({
        title: err.title || 'Investigation Failed',
        detail: err.detail || err.message || 'An unexpected error occurred during investigation.',
        status: err.status || 500,
        correlationId: err.correlationId,
      });
      setStatus('error');
      return null;
    }
  }, []);

  const clearError = useCallback(() => setError(null), []);
  const reset = useCallback(() => {
    setStatus('idle');
    setReport(null);
    setGeneratedAt(null);
    setCurrentIncident(null);
    setError(null);
  }, []);

  return {
    status,
    isLoading: status === 'loading',
    report,
    generatedAt,
    setReport,
    currentIncident,
    setCurrentIncident,
    error,
    setError,
    clearError,
    runInvestigation,
    reset,
  };
}
