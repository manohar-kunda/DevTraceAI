import React from 'react';
import { DEMO_INCIDENT_A_LABEL, DEMO_INCIDENT_B_LABEL } from '../../demo/demoData.js';

export function DemoButtons({ onLoadDemoA, onLoadDemoB, disabled }) {
  return (
    <>
      <button
        type="button"
        className="btn btn-secondary"
        onClick={onLoadDemoA}
        disabled={disabled}
      >
        {DEMO_INCIDENT_A_LABEL}
      </button>
      <button
        type="button"
        className="btn btn-secondary"
        onClick={onLoadDemoB}
        disabled={disabled}
      >
        {DEMO_INCIDENT_B_LABEL}
      </button>
    </>
  );
}
