import React from 'react';
import { pluralize } from '../../utils/format.js';

export function DependencyChain({ pathsToCandidates, symptomServiceId, topSuspectServiceId }) {
  if (!pathsToCandidates || Object.keys(pathsToCandidates).length === 0) {
    return null;
  }

  return (
    <div style={{ marginBottom: '16px' }}>
      <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginBottom: '8px' }}>
        Directed call path from the failing symptom service down to candidate root cause (failure cascades upstream):
      </p>
      {Object.entries(pathsToCandidates).map(([svc, p]) => (
        <div key={svc} style={{ marginBottom: '10px' }}>
          <div className="chain-container">
            {p.serviceIds.map((nodeId, idx) => {
              const isSymptom = nodeId === symptomServiceId;
              const isSuspect = nodeId === topSuspectServiceId;
              const cls = isSymptom ? 'chain-node symptom' : isSuspect ? 'chain-node suspect' : 'chain-node';

              return (
                <React.Fragment key={nodeId}>
                  <div className={cls} title={isSymptom ? 'Failing service (symptom)' : isSuspect ? 'Top suspect service' : 'Intermediate dependency'}>
                    {nodeId} {isSymptom ? '(failing)' : isSuspect ? '(suspect)' : ''}
                  </div>
                  {idx < p.serviceIds.length - 1 && (
                    <span className="chain-arrow">──calls──►</span>
                  )}
                </React.Fragment>
              );
            })}
          </div>
          <span style={{ fontSize: '12px', color: 'var(--text-subtle)', marginLeft: '4px' }}>
            {p.hopCount} {pluralize(p.hopCount, 'call away', 'calls away')} from failing service
          </span>
        </div>
      ))}
    </div>
  );
}
