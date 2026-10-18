import React from 'react';

export function EntryPoints({ entryPoints }) {
  if (!entryPoints) return null;

  const { service, errorSignature } = entryPoints;

  return (
    <div style={{ marginBottom: '16px' }}>
      <h3 className="section-title">A. Resolved Entry Points</h3>
      <div className="kv-grid">
        <div className="kv-item">
          <span className="kv-label">Symptom Service</span>
          <span className="kv-value">
            {service?.serviceName || 'Unknown'} ({service?.serviceId || 'None'})
          </span>
          <div style={{ fontSize: '12px', color: 'var(--text-main)', marginTop: '6px' }}>
            <strong>Matched phrase:</strong> <code>'{service?.evidence?.matchedToken || 'N/A'}'</code> |{' '}
            <strong>Match Source:</strong> <code>{service?.evidence?.matchSource || 'N/A'}</code>
          </div>
          {service?.evidence?.explanation && (
            <span style={{ fontSize: '11px', color: 'var(--text-subtle)', marginTop: '4px', display: 'block' }}>
              {service.evidence.explanation}
            </span>
          )}
        </div>

        <div className="kv-item">
          <span className="kv-label">Matched Error Signature</span>
          <span className="kv-value">
            {errorSignature?.errorType || 'Unknown'} ({errorSignature?.signatureId || 'None'})
          </span>
          <div style={{ fontSize: '12px', color: 'var(--text-main)', marginTop: '6px' }}>
            <strong>Matched phrase:</strong> <code>'{errorSignature?.evidence?.matchedToken || 'N/A'}'</code> |{' '}
            <strong>Match Source:</strong> <code>{errorSignature?.evidence?.matchSource || 'N/A'}</code>
          </div>
          {errorSignature?.pattern && (
            <div style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '4px' }}>
              <strong>Pattern:</strong> <code>{errorSignature.pattern}</code>
            </div>
          )}
          {errorSignature?.evidence?.explanation && (
            <span style={{ fontSize: '11px', color: 'var(--text-subtle)', marginTop: '4px', display: 'block' }}>
              {errorSignature.evidence.explanation}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}
