import { useState, useCallback } from 'react';
import { createIncident, investigate } from '../api/endpoints.js';

export function useInvestigation() {
  const [status, setStatus] = useState('idle'); // 'idle' | 'loading' | 'success' | 'error'
  const [report, setReport] = useState(null);
  const [currentIncident, setCurrentIncident] = useState(null);
  const [error, setError] = useState(null);

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
      setCurrentIncident(inc);

      // 2. Perform investigation
      const rep = await investigate(targetId, payload);
      setReport(rep);
      setStatus('success');
      return { incident: inc, report: rep };
    } catch (err) {
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
    setCurrentIncident(null);
    setError(null);
  }, []);

  return {
    status,
    isLoading: status === 'loading',
    report,
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
