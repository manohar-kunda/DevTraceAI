import React from 'react';

export function NextSteps({ suggestedNextSteps }) {
  if (!suggestedNextSteps || suggestedNextSteps.length === 0) {
    return <p style={{ fontSize: '13px', color: 'var(--text-subtle)' }}>No immediate actions suggested.</p>;
  }

  return (
    <div>
      {suggestedNextSteps.map((step, idx) => (
        <div key={idx} className="step-item" style={{
          backgroundColor: '#eff6ff',
          borderLeft: '4px solid var(--brand-blue)',
          padding: '12px 16px',
          borderRadius: '0 8px 8px 0',
          marginBottom: '10px',
        }}>
          <div className="step-label" style={{ fontWeight: 600, fontSize: '14px', color: '#1e40af' }}>
            {idx + 1}. {step.label}
          </div>
          <div className="step-desc" style={{ fontSize: '13px', color: '#334155', marginTop: '4px' }}>
            {step.description}
          </div>
        </div>
      ))}
    </div>
  );
}
