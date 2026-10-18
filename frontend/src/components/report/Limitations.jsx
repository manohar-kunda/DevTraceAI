import React from 'react';

export function Limitations({ limitations }) {
  if (!limitations || limitations.length === 0) {
    return null;
  }

  return (
    <ul className="limitation-list" style={{ marginLeft: '20px', color: 'var(--text-muted)', fontSize: '13px' }}>
      {limitations.map((lim, idx) => (
        <li key={idx} style={{ marginBottom: '6px', lineHeight: 1.5 }}>
          {lim}
        </li>
      ))}
    </ul>
  );
}
