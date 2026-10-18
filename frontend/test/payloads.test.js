import test, { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  buildResolutionPayload,
  buildIncidentPayload,
  parseErrorResponse,
} from '../src/api/payloads.js';
import { DEMO_RESOLUTION_A } from '../src/demo/demoData.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

describe('Payload Builders & Error Parsing Regression Tests', () => {
  it('(i) Resolution payload field names exactly match docs/examples/demo-resolution-a.json and docs/API.md', () => {
    // Read reference file directly from repository
    const examplePath = path.resolve(__dirname, '../../docs/examples/demo-resolution-a.json');
    const exampleContent = JSON.parse(fs.readFileSync(examplePath, 'utf8'));
    const expectedKeys = Object.keys(exampleContent).sort();

    const builtPayload = buildResolutionPayload(DEMO_RESOLUTION_A);
    const actualKeys = Object.keys(builtPayload).sort();

    assert.deepEqual(actualKeys, expectedKeys, 'Payload keys must match docs/examples/demo-resolution-a.json');
    assert.equal(actualKeys.includes('resolutionTitle'), false, 'Key "resolutionTitle" must NOT be in the API payload');
    assert.equal(actualKeys.includes('title'), true, 'Key "title" must be present');
    assert.equal(builtPayload.title, DEMO_RESOLUTION_A.resolutionTitle);
  });

  it('(ii) Empty optional fields are omitted from payloads', () => {
    // When triggeringDeploymentId is empty string, spaces, or null, it must be omitted
    const resWithEmptyDep = buildResolutionPayload({
      ...DEMO_RESOLUTION_A,
      triggeringDeploymentId: '   ',
    });
    assert.equal(
      Object.prototype.hasOwnProperty.call(resWithEmptyDep, 'triggeringDeploymentId'),
      false,
      'Empty triggeringDeploymentId must be omitted from resolution payload'
    );

    // When provided, it must be present
    const resWithDep = buildResolutionPayload({
      ...DEMO_RESOLUTION_A,
      triggeringDeploymentId: 'dep-payment-v2.4.1',
    });
    assert.equal(resWithDep.triggeringDeploymentId, 'dep-payment-v2.4.1');

    // Incident payload: empty hints and epoch must be omitted
    const incMinimal = buildIncidentPayload({
      description: 'Checkout failure',
      serviceHint: '',
      errorHint: '   ',
      startedAt: '',
      incidentId: '',
    });
    assert.deepEqual(Object.keys(incMinimal), ['description']);

    // Incident payload: when provided, they must be present
    const incFull = buildIncidentPayload({
      description: 'Checkout failure',
      serviceHint: 'service-checkout',
      errorHint: 'err-sig-timeout',
      startedAt: '1792058400000',
      incidentId: 'inc-001',
    });
    assert.equal(incFull.serviceHint, 'service-checkout');
    assert.equal(incFull.errorHint, 'err-sig-timeout');
    assert.equal(incFull.startedAt, 1792058400000);
    assert.equal(incFull.incidentId, 'inc-001');
  });

  it('(iii) Error parser handles problem+json 400 with fields, empty-body 400, and non-JSON 502', () => {
    // 1. problem+json 400 with fieldErrors
    const problemJson = {
      title: 'Bad Request',
      detail: 'Validation failed for one or more request fields.',
      status: 400,
      fieldErrors: { title: 'title is required' },
      correlationId: 'corr-abc-123',
    };
    const err1 = parseErrorResponse(400, 'application/problem+json', problemJson, null, '/api/incidents/inc-1/resolution');
    assert.equal(err1.status, 400);
    assert.equal(err1.title, 'Bad Request');
    assert.match(err1.detail, /Validation failed/);
    assert.match(err1.detail, /title: title is required/);
    assert.equal(err1.fieldErrors?.title, 'title is required');
    assert.equal(err1.correlationId, 'corr-abc-123');

    // 2. Empty-body 400
    const err2 = parseErrorResponse(400, 'application/problem+json', null, '', '/api/incidents/inc-1');
    assert.equal(err2.status, 400);
    assert.equal(err2.detail, 'HTTP error 400');
    assert.equal(err2.title, 'Request Failed');

    // 3. Non-JSON 502
    const htmlBody = '<html><body>502 Bad Gateway</body></html>';
    const err3 = parseErrorResponse(502, 'text/html', null, htmlBody, '/api/health');
    assert.equal(err3.status, 502);
    assert.equal(err3.detail, htmlBody);
    assert.equal(err3.fieldErrors, null);
  });
});
