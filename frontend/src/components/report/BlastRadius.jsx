import React from 'react';
import { pluralize } from '../../utils/format.js';

export function BlastRadius({ blastRadius }) {
  if (!blastRadius) return null;

  const { targetServiceId, directDependents = [], indirectDependents = [] } = blastRadius;
  const total = directDependents.length + indirectDependents.length;

  return (
    <div style={{ marginTop: '14px', marginBottom: '14px' }}>
      <span className="kv-label">
        Who is affected if {targetServiceId} fails ({total} upstream service{total === 1 ? '' : 's'}):
      </span>
      <div className="tag-group">
        {directDependents.map((d) => (
          <span
            key={d.serviceId}
            className="tag"
            style={{ borderLeft: '3px solid #2563eb' }}
            title="Direct caller (1 call away)"
          >
            {d.serviceName} (1 call away)
          </span>
        ))}
        {indirectDependents.map((d) => (
          <span
            key={d.serviceId}
            className="tag"
            style={{ borderLeft: '3px solid #94a3b8' }}
            title={`Indirect caller (${d.hops} calls away)`}
          >
            {d.serviceName} ({d.hops} {pluralize(d.hops, 'call away', 'calls away')})
          </span>
        ))}
      </div>
    </div>
  );
}
