import React from 'react';

export function Section({ title, subtitle, action, children, className = '' }) {
  return (
    <section className={`card ${className}`}>
      {title && (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '8px' }}>
          <div>
            <h2 className="card-title">{title}</h2>
            {subtitle && <p className="card-subtitle">{subtitle}</p>}
          </div>
          {action && <div>{action}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

export function AccordionSection({ title, defaultOpen = false, children, count }) {
  return (
    <details className="section-accordion" open={defaultOpen}>
      <summary>
        <span>
          {title} {typeof count === 'number' ? `(${count})` : ''}
        </span>
      </summary>
      <div className="accordion-body">{children}</div>
    </details>
  );
}
