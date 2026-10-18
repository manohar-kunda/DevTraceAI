import React from 'react';
import { TierBadge } from '../common/Badge.jsx';
import { formatIso } from '../../utils/format.js';

export function HypothesisCard({ hypothesis }) {
  if (!hypothesis) return null;

  const distanceText = hypothesis.hopDistance === 0
    ? 'On the failing service itself'
    : `${hypothesis.hopDistance} ${hypothesis.hopDistance === 1 ? 'call' : 'calls'} away from the failing service`;

  return (
    <div className="hypothesis-card">
      <div className="hypothesis-header">
        <span style={{ fontWeight: 700, fontSize: '15px' }}>
          #{hypothesis.rank} {hypothesis.serviceName} ({hypothesis.serviceId})
        </span>
        <TierBadge tier={hypothesis.tier} />
      </div>

      <p style={{ fontSize: '14px', color: 'var(--text-body)', marginBottom: '8px' }}>
        <strong>Why we think so:</strong> {hypothesis.plainLanguageReason}
      </p>

      <div style={{ fontSize: '12px', color: 'var(--text-subtle)', marginBottom: '8px' }}>
        Location: <strong>{distanceText}</strong> | Most Recent Change:{' '}
        <strong>{formatIso(hypothesis.mostRecentChangeTimestamp)}</strong>
      </div>

      {hypothesis.evidence && hypothesis.evidence.length > 0 && (
        <ul className="evidence-list">
          {hypothesis.evidence.map((ev, idx) => (
            <li key={idx} className="evidence-item">
              <strong>[{ev.evidenceType}]</strong> {ev.description}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
