/**
 * REST API Endpoints for DevTrace AI backend service.
 */
import { request } from './client.js';

export async function getGoldenIncident() {
  return request('/api/demo/golden-incident', { method: 'GET' });
}

export async function createIncident(payload) {
  return request('/api/incidents', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function getIncident(incidentId) {
  return request(`/api/incidents/${incidentId}`, { method: 'GET' });
}

export async function investigate(incidentId, payload) {
  return request(`/api/incidents/${incidentId}/investigation`, {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function confirmResolution(incidentId, payload, token) {
  return request(`/api/incidents/${incidentId}/resolution`, {
    method: 'POST',
    headers: {
      'X-DevTrace-Token': token ? token.trim() : '',
    },
    body: JSON.stringify(payload),
  });
}

export async function getHealth() {
  return request('/api/health', { method: 'GET' });
}
