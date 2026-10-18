import React, { useEffect, useRef } from 'react';
import { formatIso } from '../../utils/format.js';
import { SourceBadge } from '../common/Badge.jsx';

export function ConfirmationBanner({ result, onTryDemoB, isDemoMode }) {
  const bannerRef = useRef(null);

  useEffect(() => {
    if (result && bannerRef.current) {
      bannerRef.current.focus();
    }
  }, [result]);

  if (!result) return null;

  return (
    <div
      ref={bannerRef}
      className="confirmed-alert"
      tabIndex="-1"
      role="region"
      aria-label="Resolution Confirmation Success"
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '8px' }}>
        <strong>✔ Resolution Confirmed & Written to FalkorDB</strong>
        <SourceBadge source={result.source} />
      </div>

      <div style={{ marginTop: '10px', fontSize: '13px', lineHeight: 1.6 }}>
        <div>Resolution ID: <code>{result.id}</code></div>
        <div>Confirmed At: <code>{formatIso(result.confirmedAt)}</code></div>
        <div>Confirmed By: <code>{result.confirmedBy}</code></div>
      </div>

      <div style={{ marginTop: '12px', paddingTop: '10px', borderTop: '1px solid #86efac', fontSize: '13px' }}>
        <strong>What changed in the knowledge graph:</strong>
        <ul style={{ paddingLeft: '18px', marginTop: '4px' }}>
          <li>Created verified <code>(:Resolution)</code> node in FalkorDB marked <code>source='user-confirmed'</code>.</li>
          <li>Linked <code>(Resolution)-[:RESOLVES]-&gt;(Incident)</code> and updated Incident status to <code>RESOLVED</code>.</li>
          <li>Linked <code>(Resolution)-[:ADDRESSES]-&gt;(Service)</code> to establish institutional memory for future incidents.</li>
        </ul>
      </div>

      {isDemoMode && onTryDemoB && (
        <div style={{ marginTop: '14px' }}>
          <button
            type="button"
            className="btn btn-primary"
            onClick={onTryDemoB}
            style={{ fontSize: '13px', padding: '6px 14px' }}
          >
            Next: try similar incident B
          </button>
        </div>
      )}
    </div>
  );
}
