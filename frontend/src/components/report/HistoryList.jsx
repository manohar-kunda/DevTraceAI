import React from 'react';
import { formatIso } from '../../utils/format.js';
import { SourceBadge } from '../common/Badge.jsx';

export function HistoryList({ matchedHistoricalIncidents }) {
  if (!matchedHistoricalIncidents || matchedHistoricalIncidents.length === 0) {
    return <p style={{ fontSize: '13px', color: 'var(--text-subtle)' }}>No historical incidents matched this error signature.</p>;
  }

  return (
    <div style={{ marginBottom: '16px' }}>
      <span className="kv-label">Matched Historical Incidents ({matchedHistoricalIncidents.length}):</span>
      <ul className="evidence-list" style={{ marginTop: '8px' }}>
        {matchedHistoricalIncidents.map((inc) => (
          <li key={inc.incidentId} className="evidence-item" style={{ borderLeftColor: '#10b981' }}>
            <strong>{inc.incidentId}</strong>: "{inc.title}" (Status: {inc.status})
            {inc.confirmedResolution && (
              <div style={{ marginTop: '6px', fontSize: '13px', color: '#166534' }}>
                Confirmed Resolution: <strong>{inc.confirmedResolution.id}</strong> (
                {inc.confirmedResolution.title}) — Source:{' '}
                <SourceBadge source={inc.confirmedResolution.source} /> Confirmed:{' '}
                <code>{formatIso(inc.confirmedResolution.confirmedAt)}</code>
              </div>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}
