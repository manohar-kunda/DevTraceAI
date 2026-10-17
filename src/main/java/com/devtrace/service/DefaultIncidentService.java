package com.devtrace.service;

import com.devtrace.dto.*;
import com.devtrace.engine.InvestigationEngine;
import com.devtrace.exception.*;
import com.devtrace.model.*;
import com.devtrace.repository.GraphRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;

@Service
public class DefaultIncidentService implements IncidentService {

    private final GraphRepository graphRepository;
    private final InvestigationEngine investigationEngine;
    private final ResolutionService resolutionService;
    private final Clock clock;

    @Autowired
    public DefaultIncidentService(
            GraphRepository graphRepository,
            InvestigationEngine investigationEngine,
            ResolutionService resolutionService,
            @Autowired(required = false) Clock clock
    ) {
        this.graphRepository = Objects.requireNonNull(graphRepository, "graphRepository cannot be null");
        this.investigationEngine = Objects.requireNonNull(investigationEngine, "investigationEngine cannot be null");
        this.resolutionService = Objects.requireNonNull(resolutionService, "resolutionService cannot be null");
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    @Override
    public IncidentCreationResult createIncident(String graphName, CreateIncidentRequest request) {
        Objects.requireNonNull(graphName, "graphName cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        String incidentId = request.incidentId() != null && !request.incidentId().isBlank()
                ? request.incidentId().trim()
                : "inc-" + UUID.randomUUID().toString().substring(0, 12);

        String title = request.description().length() > 120
                ? request.description().substring(0, 117) + "..."
                : request.description();

        long reportedAt = (request.startedAt() != null && request.startedAt() > 0)
                ? request.startedAt()
                : clock.millis();

        // Check if an incident with this ID already exists
        Optional<IncidentRecord> existingOpt = graphRepository.findIncidentById(graphName, incidentId);
        if (existingOpt.isPresent()) {
            IncidentRecord existing = existingOpt.get();
            // Check for payload equivalence
            boolean sameTitle = Objects.equals(existing.title(), title)
                    || existing.title().startsWith(request.description().substring(0, Math.min(20, request.description().length())));

            if (sameTitle) {
                // Idempotent hit: return 200 with existing record
                return new IncidentCreationResult(
                        new CreateIncidentResponse(existing.id(), existing.title(), existing.status(), existing.reportedAt(), existing.severity(), false),
                        false
                );
            } else {
                throw new IncidentConflictException(
                        "Incident " + incidentId + " already exists with differing details: '" + existing.title() + "'"
                );
            }
        }

        IncidentInput input = new IncidentInput(
                incidentId,
                title,
                "OPEN",
                reportedAt,
                "P1",
                request.serviceHint(),
                request.errorHint()
        );

        IncidentRecord created = graphRepository.createOrEnsureIncident(graphName, input);
        return new IncidentCreationResult(
                new CreateIncidentResponse(created.id(), created.title(), created.status(), created.reportedAt(), created.severity(), true),
                true
        );
    }

    @Override
    public IncidentStatusResponse getIncident(String graphName, String incidentId) {
        Objects.requireNonNull(graphName, "graphName cannot be null");
        Objects.requireNonNull(incidentId, "incidentId cannot be null");

        IncidentRecord incident = graphRepository.findIncidentById(graphName, incidentId)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found with ID: " + incidentId));

        ResolutionRecord resolution = graphRepository.findResolutionByIncidentId(graphName, incidentId).orElse(null);

        return new IncidentStatusResponse(
                incident.id(),
                incident.title(),
                incident.status(),
                incident.reportedAt(),
                incident.severity(),
                resolution
        );
    }

    @Override
    public InvestigationReport investigateIncident(String graphName, String incidentId, InvestigationRequest overrideRequest) {
        Objects.requireNonNull(graphName, "graphName cannot be null");
        Objects.requireNonNull(incidentId, "incidentId cannot be null");

        IncidentRecord incident = graphRepository.findIncidentById(graphName, incidentId)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found with ID: " + incidentId));

        String description = (overrideRequest != null && overrideRequest.description() != null && !overrideRequest.description().isBlank())
                ? overrideRequest.description()
                : incident.title();

        String serviceHint = (overrideRequest != null && overrideRequest.serviceHint() != null && !overrideRequest.serviceHint().isBlank())
                ? overrideRequest.serviceHint()
                : incident.symptomServiceId();

        String errorHint = (overrideRequest != null && overrideRequest.errorHint() != null && !overrideRequest.errorHint().isBlank())
                ? overrideRequest.errorHint()
                : incident.errorSignatureId();

        Long startedAt = (overrideRequest != null && overrideRequest.startedAt() != null && overrideRequest.startedAt() > 0)
                ? overrideRequest.startedAt()
                : (incident.reportedAt() > 0 ? incident.reportedAt() : null);

        InvestigationRequest effectiveRequest = new InvestigationRequest(
                description,
                serviceHint,
                errorHint,
                startedAt,
                incidentId
        );

        return investigationEngine.investigate(graphName, effectiveRequest);
    }

    @Override
    public BlastRadiusResponse getBlastRadius(String graphName, String serviceId, int maxDepth) {
        Objects.requireNonNull(graphName, "graphName cannot be null");
        Objects.requireNonNull(serviceId, "serviceId cannot be null");

        if (!graphRepository.serviceExists(graphName, serviceId)) {
            throw new ServiceNotFoundException("Service not found in graph: " + serviceId);
        }

        int cappedDepth = Math.min(Math.max(1, maxDepth), 10);
        DependentsResult result = graphRepository.findDependents(graphName, serviceId, cappedDepth, 50);

        return new BlastRadiusResponse(
                result.targetServiceId(),
                result.directDependents(),
                result.indirectDependents(),
                result.allDependents().size()
        );
    }

    @Override
    public ResolutionConfirmationResult confirmResolution(String graphName, String incidentId, ConfirmResolutionRequest request) {
        Objects.requireNonNull(graphName, "graphName cannot be null");
        Objects.requireNonNull(incidentId, "incidentId cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        // Verify incident exists
        IncidentRecord incident = graphRepository.findIncidentById(graphName, incidentId)
                .orElseThrow(() -> new IncidentNotFoundException("Incident not found with ID: " + incidentId));

        // Check if resolution already exists before calling resolution service
        Optional<ResolutionRecord> existingResOpt = graphRepository.findResolutionByIncidentId(graphName, incidentId);
        boolean alreadyResolved = existingResOpt.isPresent();

        ResolutionInput input = new ResolutionInput(
                request.rootCauseServiceId(),
                request.title(),
                request.remediationType(),
                request.actionTaken(),
                request.confirmedBy(),
                request.triggeringDeploymentId()
        );

        ResolutionRecord confirmed = resolutionService.confirmResolution(graphName, incidentId, input);
        return new ResolutionConfirmationResult(confirmed, !alreadyResolved);
    }
}
