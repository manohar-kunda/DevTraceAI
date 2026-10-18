/**
 * HTTP client wrapper with robust RFC 7807 ProblemDetail parsing.
 * Supports application/problem+json, application/json, and text/plain.
 * Sanitizes errors so internal stack traces are never surfaced to the UI.
 */
import { parseErrorResponse } from './payloads.js';

export const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8085';

export async function request(path, options = {}) {
  const url = `${API_BASE}${path}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };

  const response = await fetch(url, {
    ...options,
    headers,
  });

  const contentType = (response.headers.get('content-type') || '').toLowerCase();
  let data = null;
  let text = null;

  if (contentType.includes('json')) {
    data = await response.json().catch(() => null);
  } else {
    text = await response.text().catch(() => null);
  }

  if (!response.ok) {
    const errorObj = parseErrorResponse(response.status, contentType, data, text, path);
    throw errorObj;
  }

  return data;
}
