import React from 'react';
import { DemoButtons } from './DemoButtons.jsx';
import { AdvancedOptions } from './AdvancedOptions.jsx';

export function IncidentForm({
  description,
  setDescription,
  serviceHint,
  setServiceHint,
  errorHint,
  setErrorHint,
  incidentId,
  setIncidentId,
  startedAt,
  setStartedAt,
  onSubmit,
  onLoadDemoA,
  onLoadDemoB,
  loading,
}) {
  return (
    <form onSubmit={onSubmit} aria-busy={loading}>
      <div className="form-grid">
        <div className="form-group">
          <label htmlFor="description">What is going wrong? *</label>
          <textarea
            id="description"
            rows="3"
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="e.g. Checkout requests are failing after the latest deployment and payment timeouts have increased"
            required
          />
          <span className="field-help-text">
            Enter the raw alert text or describe symptoms. DevTrace AI scans for mentioned services and error signatures.
          </span>
        </div>

        <AdvancedOptions
          serviceHint={serviceHint}
          setServiceHint={setServiceHint}
          errorHint={errorHint}
          setErrorHint={setErrorHint}
          incidentId={incidentId}
          setIncidentId={setIncidentId}
          startedAt={startedAt}
          setStartedAt={setStartedAt}
        />
      </div>

      <div className="form-actions">
        <DemoButtons
          onLoadDemoA={onLoadDemoA}
          onLoadDemoB={onLoadDemoB}
          disabled={loading}
        />
        <button
          type="submit"
          className="btn btn-primary"
          disabled={loading}
        >
          {loading ? 'Investigating Topology...' : 'Investigate Incident'}
        </button>
      </div>
    </form>
  );
}
