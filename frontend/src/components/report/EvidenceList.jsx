import React from 'react';
import { formatIso, formatMinutesBefore } from '../../utils/format.js';

export function EvidenceList({ deploymentsInWindow, startedAt }) {
  if (!deploymentsInWindow || deploymentsInWindow.length === 0) {
    return <p style={{ fontSize: '13px', color: 'var(--text-subtle)' }}>No recent deployments detected in window.</p>;
  }

  return (
    <div style={{ marginBottom: '16px' }}>
      <span className="kv-label">Deployments in Analysis Window ({deploymentsInWindow.length}):</span>
      <ul className="evidence-list" style={{ marginTop: '8px' }}>
        {deploymentsInWindow.map((dep) => {
          const timing = formatMinutesBefore(startedAt, dep.deployedAt);
          return (
            <li key={dep.deploymentId} className="evidence-item" style={{ borderLeftColor: '#3b82f6' }}>
              <strong>{dep.deploymentId}</strong> on <em>{dep.serviceId}</em> ({dep.version}) deployed at{' '}
              <code>{formatIso(dep.deployedAt)}</code> ({timing}): {dep.changeSummary}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
