import React from 'react';

export function DemoGuide({ currentStep = 1, onNextStep }) {
  const steps = [
    { num: 1, label: '1. Run incident A' },
    { num: 2, label: '2. Read result' },
    { num: 3, label: '3. Confirm fix A' },
    { num: 4, label: '4. Run similar incident B' },
  ];

  return (
    <div className="demo-guide-card" role="region" aria-label="Demo Guide">
      <div className="demo-guide-title">
        <span>● Guided Demo Progress</span>
        {onNextStep && currentStep < 4 && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={onNextStep}
            style={{ padding: '4px 10px', fontSize: '12px' }}
          >
            Next Demo Step ➔
          </button>
        )}
      </div>
      <div className="demo-guide-steps">
        {steps.map((s) => (
          <span
            key={s.num}
            className={`demo-guide-step-pill ${currentStep === s.num ? 'active' : ''}`}
          >
            {s.label}
          </span>
        ))}
      </div>
    </div>
  );
}
