import React from 'react';

export function ErrorBanner({ error, onDismiss, onRetry }) {
  if (!error) return null;

  return (
    <div className="error-banner" role="alert" aria-live="assertive">
      <div className="error-banner-header">
        <span>
          {error.title || 'Error'} {error.status ? `(${error.status})` : ''}
        </span>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {onRetry && (
            <button
              type="button"
              onClick={onRetry}
              className="btn btn-secondary"
              style={{ padding: '2px 8px', fontSize: '12px' }}
            >
              Retry
            </button>
          )}
          {onDismiss && (
            <button
              type="button"
              onClick={onDismiss}
              className="error-close-btn"
              aria-label="Dismiss error notification"
            >
              ✕
            </button>
          )}
        </div>
      </div>
      <p className="error-banner-detail">{error.detail}</p>
      {error.fieldErrors && typeof error.fieldErrors === 'object' && (
        <ul style={{ marginTop: '8px', paddingLeft: '18px', fontSize: '13px' }}>
          {Object.entries(error.fieldErrors).map(([field, msg]) => (
            <li key={field}>
              <strong>{field}:</strong> {msg}
            </li>
          ))}
        </ul>
      )}
      {error.correlationId && (
        <p className="error-banner-meta">Correlation ID: {error.correlationId}</p>
      )}
    </div>
  );
}
