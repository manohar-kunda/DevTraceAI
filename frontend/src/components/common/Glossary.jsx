import React from 'react';

export function Glossary() {
  return (
    <details className="glossary-box">
      <summary style={{ cursor: 'pointer', fontWeight: 600, color: '#2563eb' }}>
        What do these terms mean?
      </summary>
      <div style={{ marginTop: '10px' }}>
        <div className="glossary-item">
          <strong>Tier (STRONG vs MODERATE):</strong> Classification of confidence. <em>STRONG</em> means a recent deployment change occurred on this service AND a past incident with the same error signature was resolved on it. <em>MODERATE</em> means one of those two conditions held.
        </div>
        <div className="glossary-item">
          <strong>Call Distance / Hop:</strong> Number of network steps from the failing service along directed <code>[:DEPENDS_ON]</code> relationships in FalkorDB.
        </div>
        <div className="glossary-item">
          <strong>Blast Radius:</strong> Upstream services that call into this service and would suffer secondary degradation if it fails.
        </div>
        <div className="glossary-item">
          <strong>Error Signature:</strong> A recognized technical failure pattern (such as connection pool exhaustion or network timeouts) detected by text matching.
        </div>
        <div className="glossary-item">
          <strong>Confirmed Resolution:</strong> A remediation action verified by an on-call human engineer and recorded in the knowledge graph with <code>user-confirmed</code> provenance.
        </div>
      </div>
    </details>
  );
}
