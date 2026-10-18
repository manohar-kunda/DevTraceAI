import React from 'react';

export function TokenField({ token, setToken, disabled }) {
  return (
    <div className="form-group">
      <label htmlFor="token">API token *</label>
      <input
        id="token"
        type="password"
        autoComplete="off"
        value={token}
        onChange={(e) => setToken(e.target.value)}
        placeholder="Paste the DEVTRACE_API_TOKEN value from .env"
        disabled={disabled}
        required
      />
      <p className="field-help-text">
        This is the shared secret you set as DEVTRACE_API_TOKEN in the backend's .env file (project root).
        The server only accepts a confirmed resolution when this value matches. It is not an account password
        or an external API key, and the browser does not store it.
      </p>
      <details className="token-help-box">
        <summary className="token-help-summary">
          Where do I find the token?
        </summary>
        <ol style={{ paddingLeft: '18px', marginTop: '6px', marginBottom: '2px', lineHeight: 1.6 }}>
          <li>Open the <code>.env</code> file in the project root (for example: <code>notepad .env</code>)</li>
          <li>Copy the text after <code>DEVTRACE_API_TOKEN=</code> (no quotes or spaces)</li>
          <li>Paste it here</li>
          <li>If you haven't set one yet, copy <code>.env.example</code> to <code>.env</code>, set a token, and restart the backend</li>
          <li>If your backend was started with a token in its shell environment, use that value instead</li>
        </ol>
      </details>
    </div>
  );
}
