/**
 * Synthetic Demo Data fixtures for Golden Flow (Incident A, Incident B, Demo Resolution)
 * Clearly labelled DEMO to indicate synthetic microservice environment.
 */

export const DEMO_INCIDENT_A_LABEL = 'Load demo incident A (DEMO)';
export const DEMO_INCIDENT_B_LABEL = 'Load demo incident B (Act 4) [DEMO]';
export const DEMO_RESOLUTION_LABEL = 'Fill demo resolution (DEMO)';

export const DEMO_INCIDENT_B = {
  incidentId: 'inc-2026-10-15-checkout-002',
  description: 'Checkout failure rates spiking again with persistent payment timeouts on card transactions',
  startedAt: '1792065600000',
  serviceHint: '',
  errorHint: '',
};

export const DEMO_RESOLUTION_A = {
  rootCauseServiceId: 'service-payment',
  resolutionTitle: 'Emergency pool size rollback and idle timeout tune on payment datasource',
  remediationType: 'CONFIG_CHANGE',
  actionTaken: 'Restored HikariCP maximumPoolSize from 10 back to 50, raised connectionTimeout to 30000ms, and verified connection wait queue dropped to zero under load.',
  confirmedBy: 'sre-oncall-lead',
  triggeringDeploymentId: '',
};
