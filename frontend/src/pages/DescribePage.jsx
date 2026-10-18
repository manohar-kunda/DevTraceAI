import React from 'react';
import { Section } from '../components/common/Section.jsx';
import { IncidentForm } from '../components/incident/IncidentForm.jsx';
import { Glossary } from '../components/common/Glossary.jsx';
import { DemoGuide } from '../components/common/DemoGuide.jsx';

export function DescribePage({
  form,
  setField,
  onSubmit,
  onLoadDemoA,
  onLoadDemoB,
  loading,
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
        title="1. Describe the Incident"
        subtitle="Provide raw alert text or describe system symptoms. DevTrace AI scans the microservice topology and analyzes connected dependencies."
      >
        <IncidentForm
          description={form.description}
          setDescription={(val) => setField('description', val)}
          serviceHint={form.serviceHint}
          setServiceHint={(val) => setField('serviceHint', val)}
          errorHint={form.errorHint}
          setErrorHint={(val) => setField('errorHint', val)}
          incidentId={form.incidentId}
          setIncidentId={(val) => setField('incidentId', val)}
          startedAt={form.startedAt}
          setStartedAt={(val) => setField('startedAt', val)}
          onSubmit={onSubmit}
          onLoadDemoA={onLoadDemoA}
          onLoadDemoB={onLoadDemoB}
          loading={loading}
        />
      </Section>

      <Glossary />
    </div>
  );
}
