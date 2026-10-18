import React from 'react';
import { TierBadge, SourceBadge, StatusBadge } from '../common/Badge.jsx';
import { formatMinutesBefore } from '../../utils/format.js';

export function ReportSummary({ report, incidentId, generatedAt, onRerun }) {
  if (!report) return null;

  const topHypothesis = report.hypotheses && report.hypotheses.length > 0
    ? report.hypotheses[0]
    : null;

  const isInsufficient = report.status === 'INSUFFICIENT_EVIDENCE';

  // Find deployment on top suspect
  const suspectDep = topHypothesis && report.observedFacts?.deploymentsInWindow
    ? report.observedFacts.deploymentsInWindow.find((d) => d.serviceId === topHypothesis.serviceId)
    : null;

  // Find historical resolution
  const histMatch = report.observedFacts?.matchedHistoricalIncidents?.find((i) => i.confirmedResolution);

  // Affected dependents
  const blast = report.graphRelationships?.blastRadius;
  const directAffectedCount = blast?.directDependents?.length || 0;
  const indirectAffectedCount = blast?.indirectDependents?.length || 0;

  return (
    <div>
      <div className="report-header">
        <div>
          <h2 id="report-heading" style={{ fontSize: '18px', fontWeight: 700 }} tabIndex="-1">
            Investigation Findings
          </h2>
          <p style={{ fontSize: '13px', color: 'var(--text-subtle)' }}>
            Incident ID: <code>{report.requestEcho?.incidentId || incidentId}</code>
            {generatedAt && (
              <span> | Report generated at: <code>{generatedAt}</code></span>
            )}
          </p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {onRerun && (
            <button
              type="button"
              className="btn btn-secondary"
              onClick={onRerun}
              style={{ padding: '4px 10px', fontSize: '12px' }}
            >
              ↻ Re-run investigation
            </button>
          )}
          <StatusBadge status={report.status} />
        </div>
      </div>

      {isInsufficient ? (
        <div className="card" style={{ background: '#fffbeb', border: '1px solid #fef3c7' }}>
          <h3 style={{ fontSize: '16px', fontWeight: 700, color: '#92400e', marginBottom: '8px' }}>
            Insufficient Evidence
          </h3>
          <p style={{ fontSize: '14px', color: '#78350f', marginBottom: '12px' }}>
            No strong or moderate root-cause hypotheses could be determined from the graph. The investigation engine checked the dependency neighborhood and recent deployments, but found no matching evidence.
          </p>
          {report.limitations && report.limitations.length > 0 && (
            <ul className="evidence-list">
              {report.limitations.map((lim, idx) => (
                <li key={idx} className="evidence-item" style={{ borderLeftColor: '#d97706' }}>
                  {lim}
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : topHypothesis ? (
        <div className="summary-lead-card">
          <div className="summary-lead-header">
            <div>
              <span style={{ fontSize: '12px', fontWeight: 700, textTransform: 'uppercase', color: '#15803d' }}>
                Most Likely Cause
              </span>
              <div className="suspect-name">
                #{topHypothesis.rank} {topHypothesis.serviceName} ({topHypothesis.serviceId})
              </div>
            </div>
            <TierBadge tier={topHypothesis.tier} />
          </div>

          <p className="lead-reason">
            <strong>Why we suspect this:</strong> {topHypothesis.plainLanguageReason}
          </p>

          <div className="lead-meta-grid">
            <div>
              <strong>Recent Change:</strong>{' '}
              {suspectDep ? (
                <span>
                  <code>{suspectDep.deploymentId}</code> ({suspectDep.version}) —{' '}
                  {formatMinutesBefore(report.requestEcho?.startedAt, suspectDep.deployedAt)}:{' '}
                  <em>{suspectDep.changeSummary}</em>
                </span>
              ) : (
                'No deployment change recorded in window'
              )}
            </div>

            <div>
              <strong>Seen Before:</strong>{' '}
              {histMatch ? (
                <span>
                  Incident <code>{histMatch.incidentId}</code> had fix{' '}
                  <strong>"{histMatch.confirmedResolution.title}"</strong>{' '}
                  <SourceBadge source={histMatch.confirmedResolution.source} />
                </span>
              ) : (
                'No prior confirmed resolution for this error signature'
              )}
            </div>

            <div>
              <strong>Who is Affected:</strong>{' '}
              <span>
                {directAffectedCount + indirectAffectedCount > 0
                  ? `${directAffectedCount} direct upstream caller(s), ${indirectAffectedCount} indirect caller(s)`
                  : 'No upstream callers in topology'}
              </span>
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
