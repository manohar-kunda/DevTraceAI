/**
 * Pure payload builders and response error parsers for DevTrace AI.
 * Ensures field names match docs/API.md and empty optional fields are omitted.
 */

export function buildResolutionPayload(formValues) {
  const payload = {
    rootCauseServiceId: (formValues.rootCauseServiceId || '').trim(),
    title: (formValues.title || formValues.resolutionTitle || '').trim(),
    remediationType: (formValues.remediationType || 'CONFIG_CHANGE').trim(),
    actionTaken: (formValues.actionTaken || '').trim(),
    confirmedBy: (formValues.confirmedBy || '').trim(),
  };

  const triggeringDeploymentId = (formValues.triggeringDeploymentId || '').trim();
  if (triggeringDeploymentId) {
    payload.triggeringDeploymentId = triggeringDeploymentId;
  }

  return payload;
}

export function buildIncidentPayload(formValues) {
  const payload = {
    description: (formValues.description || '').trim(),
  };

  const serviceHint = (formValues.serviceHint || '').trim();
  if (serviceHint) {
    payload.serviceHint = serviceHint;
  }

  const errorHint = (formValues.errorHint || '').trim();
  if (errorHint) {
    payload.errorHint = errorHint;
  }

  if (formValues.startedAt !== undefined && formValues.startedAt !== null && formValues.startedAt !== '') {
    const num = Number(formValues.startedAt);
    if (!isNaN(num) && num > 0) {
      payload.startedAt = num;
    }
  }

  const incidentId = (formValues.incidentId || '').trim();
  if (incidentId) {
    payload.incidentId = incidentId;
  }

  return payload;
}

export function parseErrorResponse(status, contentType = '', bodyData = null, bodyText = null, instance = '') {
  let detail = bodyData?.detail || bodyData?.message || bodyText || `HTTP error ${status}`;

  if (bodyData?.fieldErrors && typeof bodyData.fieldErrors === 'object') {
    const fieldList = Object.entries(bodyData.fieldErrors)
      .map(([field, msg]) => `${field}: ${msg}`)
      .join('; ');
    if (fieldList) {
      detail = `${detail} (${fieldList})`;
    }
  }

  return {
    status,
    title: bodyData?.title || (status === 401 ? 'Unauthorized (401)' : 'Request Failed'),
    detail,
    correlationId: bodyData?.correlationId || null,
    fieldErrors: bodyData?.fieldErrors || null,
    instance: bodyData?.instance || instance,
    type: bodyData?.type || null,
  };
}
