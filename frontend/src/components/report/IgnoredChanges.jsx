import React from 'react';
import { formatIso } from '../../utils/format.js';

export function IgnoredChanges({ ignoredChanges }) {
  if (!ignoredChanges || ignoredChanges.length === 0) {
    return <p style={{ fontSize: '13px', color: 'var(--text-subtle)' }}>No deployments were excluded from analysis.</p>;
  }

  return (
    <ul className="evidence-list">
      {ignoredChanges.map((c) => (
        <li key={c.changeId} className="evidence-item" style={{ borderLeftColor: '#94a3b8' }}>
          <strong>{c.changeId}</strong> on <em>{c.serviceName}</em> ({c.changeType} at {formatIso(c.timestamp)}):{' '}
          {c.reason}
        </li>
      ))}
    </ul>
  );
}
