/**
 * HTTP client wrapper with robust RFC 7807 ProblemDetail parsing and 15s AbortController timeout.
 * Supports application/problem+json, application/json, and text/plain.
 * Sanitizes errors so internal stack traces are never surfaced to the UI.
 */
import { parseErrorResponse } from './payloads.js';

export const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8085';

export async function request(path, options = {}, timeoutMs = 15000) {
  const url = `${API_BASE}${path}`;
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  };

  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(url, {
      ...options,
      headers,
      signal: options.signal || controller.signal,
    });
    clearTimeout(timeoutId);

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
  } catch (err) {
    clearTimeout(timeoutId);
    if (err.name === 'AbortError') {
      throw {
        status: 408,
        title: 'Request Timed Out',
        detail: 'Request timed out after 15 seconds. Please check backend availability.',
      };
    }
    throw err;
  }
}
