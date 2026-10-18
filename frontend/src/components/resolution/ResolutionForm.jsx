import React from 'react';
import { TokenField } from './TokenField.jsx';
import { DEMO_RESOLUTION_LABEL } from '../../demo/demoData.js';

export function ResolutionForm({
  incidentId,
  candidateServices = [],
  rootCauseServiceId,
  setRootCauseServiceId,
  remediationType,
  setRemediationType,
  confirmedBy,
  setConfirmedBy,
  resolutionTitle,
  setResolutionTitle,
  actionTaken,
  setActionTaken,
  triggeringDeploymentId,
  setTriggeringDeploymentId,
  token,
  setToken,
  onFillDemo,
  onSubmit,
  resolving,
}) {
  return (
    <form onSubmit={onSubmit} aria-busy={resolving}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px', marginBottom: '14px' }}>
        <p style={{ fontSize: '13px', color: 'var(--text-subtle)', margin: 0 }}>
          Only save this after you have checked that the fix worked. It becomes knowledge for future investigations.
        </p>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={onFillDemo}
          disabled={resolving}
          style={{ fontSize: '12px', padding: '4px 10px' }}
        >
          {DEMO_RESOLUTION_LABEL}
        </button>
      </div>

      <div style={{ padding: '8px 12px', background: 'var(--brand-blue-light)', border: '1px solid #bfdbfe', borderRadius: '6px', marginBottom: '16px', fontSize: '13px' }}>
        <strong>You are confirming a fix for incident:</strong> <code>{incidentId}</code>
      </div>

      <div className="form-grid">
        <div className="form-row">
          <div className="form-group">
            <label htmlFor="rootCauseServiceId">Which service was the real cause? *</label>
            <select
              id="rootCauseServiceId"
              value={rootCauseServiceId}
              onChange={(e) => setRootCauseServiceId(e.target.value)}
              disabled={resolving}
              required
            >
              {candidateServices.length === 0 && (
                <option value="">Select candidate service</option>
              )}
              {candidateServices.map((svc) => (
                <option key={svc.id} value={svc.id}>
                  {svc.label || svc.name}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label htmlFor="remediationType">What kind of fix? *</label>
            <select
              id="remediationType"
              value={remediationType}
              onChange={(e) => setRemediationType(e.target.value)}
              disabled={resolving}
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
            <label htmlFor="confirmedBy">Your name or team *</label>
            <input
              id="confirmedBy"
              type="text"
              value={confirmedBy}
              onChange={(e) => setConfirmedBy(e.target.value)}
              placeholder="e.g. sre-oncall-lead"
              disabled={resolving}
              required
            />
          </div>
        </div>

        <div className="form-group">
          <label htmlFor="resolutionTitle">Short title *</label>
          <input
            id="resolutionTitle"
            type="text"
            value={resolutionTitle}
            onChange={(e) => setResolutionTitle(e.target.value)}
            placeholder="e.g. Emergency pool size rollback and idle timeout tune on payment datasource"
            disabled={resolving}
            required
          />
        </div>

        <div className="form-group">
          <label htmlFor="actionTaken">What exactly was done? *</label>
          <textarea
            id="actionTaken"
            rows="2"
            value={actionTaken}
            onChange={(e) => setActionTaken(e.target.value)}
            placeholder="Detailed notes of configuration changes, pool adjustments, or commands executed..."
            disabled={resolving}
            required
          />
        </div>

        <div className="form-row">
          <div className="form-group">
            <label htmlFor="triggeringDeploymentId">Related deployment (optional)</label>
            <input
              id="triggeringDeploymentId"
              type="text"
              value={triggeringDeploymentId}
              onChange={(e) => setTriggeringDeploymentId(e.target.value)}
              placeholder="e.g. dep-payment-v2.4.1"
              disabled={resolving}
            />
          </div>

          <TokenField
            token={token}
            setToken={setToken}
            disabled={resolving}
          />
        </div>
      </div>

      <div className="form-actions">
        <button
          type="submit"
          className="btn btn-success"
          disabled={resolving}
        >
          {resolving ? 'Writing back to FalkorDB...' : 'Save confirmed fix'}
        </button>
      </div>
    </form>
  );
}
