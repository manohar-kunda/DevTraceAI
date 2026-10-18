import React, { useEffect } from 'react';
import { Section, AccordionSection } from '../components/common/Section.jsx';
import { ReportSummary } from '../components/report/ReportSummary.jsx';
import { EntryPoints } from '../components/report/EntryPoints.jsx';
import { EvidenceList } from '../components/report/EvidenceList.jsx';
import { HistoryList } from '../components/report/HistoryList.jsx';
import { HypothesisCard } from '../components/report/HypothesisCard.jsx';
import { DependencyChain } from '../components/report/DependencyChain.jsx';
import { BlastRadius } from '../components/report/BlastRadius.jsx';
import { NextSteps } from '../components/report/NextSteps.jsx';
import { IgnoredChanges } from '../components/report/IgnoredChanges.jsx';
import { Limitations } from '../components/report/Limitations.jsx';
import { DemoGuide } from '../components/common/DemoGuide.jsx';
import { Glossary } from '../components/common/Glossary.jsx';

export function ResultPage({
  report,
  incidentId,
  onProceedToConfirm,
  isDemoMode,
  demoStep,
  onNextDemoStep,
}) {
  useEffect(() => {
    const heading = document.getElementById('report-heading');
    if (heading) {
      heading.focus();
    }
  }, []);

  if (!report) {
    return (
      <Section title="No Investigation Results">
        <p style={{ color: 'var(--text-subtle)', fontSize: '14px' }}>
          No investigation has been run yet. Please describe an incident first.
        </p>
      </Section>
    );
  }

  const symptomSvc = report.entryPoints?.service?.serviceId;
  const topSuspectSvc = report.hypotheses?.[0]?.serviceId;
  const otherHypotheses = report.hypotheses?.slice(1) || [];

  return (
    <div>
      {isDemoMode && (
        <DemoGuide currentStep={demoStep} onNextStep={onNextDemoStep} />
      )}

      <Section>
        {/* 1. Lead Summary Card */}
        <ReportSummary report={report} incidentId={incidentId} />

        {/* Action Button to Proceed to Confirm Step */}
        <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: '16px', marginBottom: '16px' }}>
          <button
            type="button"
            className="btn btn-primary"
            onClick={onProceedToConfirm}
          >
            Confirm Root Cause & Fix ➔
          </button>
        </div>

        {/* 2. Evidence Accordion (Open by Default) */}
        <AccordionSection title="Why we think so (Evidence & Entry Points)" defaultOpen={true}>
          <EntryPoints entryPoints={report.entryPoints} />
          <EvidenceList
            deploymentsInWindow={report.observedFacts?.deploymentsInWindow}
            startedAt={report.requestEcho?.startedAt}
          />
          <HistoryList
            matchedHistoricalIncidents={report.observedFacts?.matchedHistoricalIncidents}
          />
        </AccordionSection>

        {/* 3. Dependency Chain & Blast Radius Accordion (Open by Default) */}
        <AccordionSection title="Dependency chain & blast radius" defaultOpen={true}>
          <DependencyChain
            pathsToCandidates={report.graphRelationships?.pathsToCandidates}
            symptomServiceId={symptomSvc}
            topSuspectServiceId={topSuspectSvc}
          />
          <BlastRadius blastRadius={report.graphRelationships?.blastRadius} />
        </AccordionSection>

        {/* 4. What else could it be (Other hypotheses and neighborhood services) */}
        <AccordionSection
          title="What else could it be (Other candidates)"
          defaultOpen={false}
          count={otherHypotheses.length + (report.otherServicesInNeighborhood?.length || 0)}
        >
          {otherHypotheses.length > 0 && (
            <div style={{ marginBottom: '16px' }}>
              <span className="kv-label">Lower-Ranked Hypotheses:</span>
              <div style={{ marginTop: '8px' }}>
                {otherHypotheses.map((h) => (
                  <HypothesisCard key={h.serviceId} hypothesis={h} />
                ))}
              </div>
            </div>
          )}

          {report.otherServicesInNeighborhood && report.otherServicesInNeighborhood.length > 0 && (
            <div>
              <span className="kv-label">
                Other Services in Neighborhood ({report.otherServicesInNeighborhood.length}):
              </span>
              <div className="tag-group">
                {report.otherServicesInNeighborhood.map((s) => (
                  <span key={s.id} className="tag">
                    {s.name} ({s.id}, {s.hops} {s.hops === 1 ? 'call' : 'calls'} away)
                  </span>
                ))}
              </div>
            </div>
          )}
        </AccordionSection>

        {/* 5. What to do next */}
        <AccordionSection
          title="What to do next"
          defaultOpen={false}
          count={report.suggestedNextSteps?.length || 0}
        >
          <NextSteps suggestedNextSteps={report.suggestedNextSteps} />
        </AccordionSection>

        {/* 6. What we ignored and why */}
        <AccordionSection
          title="What we ignored and why"
          defaultOpen={false}
          count={report.ignoredChanges?.length || 0}
        >
          <IgnoredChanges ignoredChanges={report.ignoredChanges} />
        </AccordionSection>

        {/* 7. How much to trust this (Reasoning boundaries & limitations) */}
        <AccordionSection
          title="How much to trust this (Reasoning boundaries & limitations)"
          defaultOpen={false}
          count={report.limitations?.length || 0}
        >
          <Limitations limitations={report.limitations} />
        </AccordionSection>
      </Section>

      <Glossary />
    </div>
  );
}
