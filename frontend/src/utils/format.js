/**
 * Formatting utilities for timestamps, relative time, and text labels.
 */

export function formatIso(timestamp) {
  if (!timestamp) return 'N/A';
  const num = Number(timestamp);
  if (isNaN(num) || num <= 0) return 'N/A';
  try {
    return new Date(num).toISOString();
  } catch {
    return 'N/A';
  }
}

export function formatMinutesBefore(refTime, targetTime) {
  const ref = Number(refTime) || Date.now();
  const target = Number(targetTime);
  if (isNaN(target)) return '';
  const mins = Math.round((ref - target) / 60000);
  if (mins >= 0) {
    return `${mins}m before incident`;
  }
  return `${Math.abs(mins)}m after incident`;
}

export function pluralize(count, singular, plural = `${singular}s`) {
  const n = Number(count) || 0;
  return `${n} ${n === 1 ? singular : plural}`;
}

export function extractCandidateServices(report) {
  const list = [];
  const seen = new Set();
  report?.hypotheses?.forEach((h) => {
    if (h.serviceId && !seen.has(h.serviceId)) {
      seen.add(h.serviceId);
      list.push({
        id: h.serviceId,
        name: h.serviceName || h.serviceId,
        label: `${h.serviceName || h.serviceId} - suspect #${h.rank}`,
      });
    }
  });
  report?.otherServicesInNeighborhood?.forEach((s) => {
    if (s.id && !seen.has(s.id)) {
      seen.add(s.id);
      list.push({
        id: s.id,
        name: s.name || s.id,
        label: `${s.name || s.id} - ${s.hops} ${s.hops === 1 ? 'call' : 'calls'} away`,
      });
    }
  });
  return list;
}
