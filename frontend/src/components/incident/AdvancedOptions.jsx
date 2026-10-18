import React from 'react';
import { formatIso } from '../../utils/format.js';

export function AdvancedOptions({
  serviceHint,
  setServiceHint,
  errorHint,
  setErrorHint,
  incidentId,
  setIncidentId,
  startedAt,
  setStartedAt,
}) {
  const utcPreview = startedAt && !isNaN(Number(startedAt)) && Number(startedAt) > 0
    ? formatIso(Number(startedAt))
    : null;

  return (
    <details
      className="section-accordion"
      style={{ marginTop: '12px', background: '#f8fafc', padding: '10px 14px' }}
    >
      <summary style={{ cursor: 'pointer', fontWeight: 600, fontSize: '14px', color: '#475569' }}>
        Advanced options (hints, ID & timestamp)
      </summary>
      <div className="accordion-body" style={{ marginTop: '12px' }}>
        <div className="form-row">
          <div className="form-group">
            <label htmlFor="serviceHint">Which service is failing? (optional)</label>
            <input
              id="serviceHint"
              type="text"
              value={serviceHint}
              onChange={(e) => setServiceHint(e.target.value)}
              placeholder="e.g. checkout-service"
            />
            <span className="field-help-text">Leave blank to test automatic service keyword resolution.</span>
          </div>

          <div className="form-group">
            <label htmlFor="errorHint">Known error type (optional)</label>
            <input
              id="errorHint"
              type="text"
              value={errorHint}
              onChange={(e) => setErrorHint(e.target.value)}
              placeholder="e.g. err-sig-payment-pool-timeout"
            />
            <span className="field-help-text">Leave blank to test error pattern detection.</span>
          </div>
        </div>

        <div className="form-row" style={{ marginTop: '12px' }}>
          <div className="form-group">
            <label htmlFor="incidentId">Incident ID (optional)</label>
            <input
              id="incidentId"
              type="text"
              value={incidentId}
              onChange={(e) => setIncidentId(e.target.value)}
              placeholder="e.g. inc-2026-10-15-checkout-001"
            />
          </div>

          <div className="form-group">
            <label htmlFor="startedAt">Incident Started At Epoch ms (optional)</label>
            <input
              id="startedAt"
              type="number"
              value={startedAt}
              onChange={(e) => setStartedAt(e.target.value)}
              placeholder="e.g. 1792058400000"
            />
            {utcPreview && (
              <span className="field-help-text" style={{ color: '#2563eb' }}>
                Readable UTC: <code>{utcPreview}</code>
              </span>
            )}
          </div>
        </div>
      </div>
    </details>
  );
}
