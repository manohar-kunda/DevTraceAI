import React from 'react';

export function TierBadge({ tier }) {
  if (!tier) return null;
  const isStrong = tier === 'STRONG';
  const title = isStrong
    ? 'STRONG: Recent deployment change on this service AND historical match for this error signature.'
    : 'MODERATE: Recent deployment change on this service OR topological candidate.';

  return (
    <span
      className={`badge ${isStrong ? 'tier-strong' : 'tier-moderate'}`}
      title={title}
      aria-label={`${tier} tier: ${title}`}
    >
      {isStrong ? '● STRONG TIER' : '○ MODERATE TIER'}
    </span>
  );
}

export function SourceBadge({ source }) {
  if (!source) return null;
  const isUser = source === 'user-confirmed';
  const label = isUser ? 'User Confirmed' : 'Synthetic Data';

  return (
    <span
      className={`badge ${isUser ? 'source-user' : 'source-synthetic'}`}
      title={`Remediation provenance: ${label}`}
      aria-label={`Source: ${label}`}
    >
      {isUser ? '✔ user-confirmed' : '● synthetic'}
    </span>
  );
}

export function StatusBadge({ status }) {
  if (!status) return null;
  const isRanked = status === 'HYPOTHESES_RANKED';

  return (
    <span
      className={`badge ${isRanked ? 'status-ranked' : 'status-insufficient'}`}
      aria-label={`Report status: ${status}`}
    >
      {isRanked ? 'HYPOTHESES RANKED' : 'INSUFFICIENT EVIDENCE'}
    </span>
  );
}
