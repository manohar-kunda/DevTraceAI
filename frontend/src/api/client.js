/**
 * HTTP client wrapper with RFC 7807 ProblemDetail parsing.
 * Sanitizes errors so internal stack traces are never surfaced to the UI.
 */

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

  const contentType = response.headers.get('content-type');
  let data = null;
  if (contentType && contentType.includes('application/json')) {
    data = await response.json().catch(() => null);
  }

  if (!response.ok) {
    const errorObj = {
      status: response.status,
      title: data?.title || (response.status === 401 ? 'Unauthorized (401)' : 'Request Failed'),
      detail: data?.detail || data?.message || `HTTP error ${response.status}`,
      correlationId: data?.correlationId || null,
      instance: data?.instance || path,
      type: data?.type || null,
    };
    throw errorObj;
  }

  return data;
}
