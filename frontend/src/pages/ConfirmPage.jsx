import React from 'react';
import { Section } from '../components/common/Section.jsx';
import { ResolutionForm } from '../components/resolution/ResolutionForm.jsx';
import { ConfirmationBanner } from '../components/resolution/ConfirmationBanner.jsx';
import { DemoGuide } from '../components/common/DemoGuide.jsx';
import { Glossary } from '../components/common/Glossary.jsx';

export function ConfirmPage({
  incidentId,
  candidateServices,
  form,
  setField,
  token,
  setToken,
  onFillDemo,
  onSubmit,
  resolving,
  resolutionResult,
  onTryDemoB,
  isDemoMode,
  demoStep,
  onNextDemoStep,
}) {
  return (
    <div>
      {isDemoMode && (
        <DemoGuide currentStep={demoStep} onNextStep={onNextDemoStep} />
      )}

      <Section
        title="Confirm the fix (human review)"
        subtitle="SRE mitigation actions are saved into the knowledge graph only upon authenticated confirmation."
      >
        <ResolutionForm
          incidentId={incidentId}
          candidateServices={candidateServices}
          rootCauseServiceId={form.rootCauseServiceId}
          setRootCauseServiceId={(val) => setField('rootCauseServiceId', val)}
          remediationType={form.remediationType}
          setRemediationType={(val) => setField('remediationType', val)}
          confirmedBy={form.confirmedBy}
          setConfirmedBy={(val) => setField('confirmedBy', val)}
          resolutionTitle={form.title || form.resolutionTitle || ''}
          setResolutionTitle={(val) => {
            setField('resolutionTitle', val);
            setField('title', val);
          }}
          actionTaken={form.actionTaken}
          setActionTaken={(val) => setField('actionTaken', val)}
          triggeringDeploymentId={form.triggeringDeploymentId}
          setTriggeringDeploymentId={(val) => setField('triggeringDeploymentId', val)}
          token={token}
          setToken={setToken}
          onFillDemo={onFillDemo}
          onSubmit={onSubmit}
          resolving={resolving}
        />

        <ConfirmationBanner
          result={resolutionResult}
          onTryDemoB={onTryDemoB}
          isDemoMode={isDemoMode}
        />
      </Section>

      <Glossary />
    </div>
  );
}
