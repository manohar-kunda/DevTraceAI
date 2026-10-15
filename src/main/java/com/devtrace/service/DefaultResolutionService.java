package com.devtrace.service;

import com.devtrace.exception.*;
import com.devtrace.model.IncidentInput;
import com.devtrace.model.IncidentRecord;
import com.devtrace.model.ResolutionInput;
import com.devtrace.model.ResolutionRecord;
import com.devtrace.repository.GraphRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Default implementation of ResolutionService.
 * Enforces strict validation, server clock isolation, and idempotency on resolution confirmation.
 */
@Service
public class DefaultResolutionService implements ResolutionService {

    private static final Logger log = LoggerFactory.getLogger(DefaultResolutionService.class);

    public static final int MAX_TITLE_LENGTH = 200;
    public static final int MAX_ACTION_TAKEN_LENGTH = 2000;
    public static final int MAX_CONFIRMED_BY_LENGTH = 100;
    public static final int MAX_REMEDIATION_TYPE_LENGTH = 50;

    private final GraphRepository graphRepository;
    private final Clock clock;

    @Autowired
    public DefaultResolutionService(GraphRepository graphRepository, @Autowired(required = false) Clock clock) {
        this.graphRepository = Objects.requireNonNull(graphRepository, "graphRepository cannot be null");
        this.clock = clock != null ? clock : Clock.systemUTC();
    }

    public DefaultResolutionService(GraphRepository graphRepository) {
        this(graphRepository, Clock.systemUTC());
    }

    @Override
    public IncidentRecord createOrEnsureIncident(String graphName, IncidentInput input) {
        Objects.requireNonNull(graphName, "graphName cannot be null");
        Objects.requireNonNull(input, "input cannot be null");

        if (input.id() == null || input.id().isBlank()) {
            throw new IllegalArgumentException("Incident id cannot be blank");
        }
        if (input.title() == null || input.title().isBlank()) {
            throw new IllegalArgumentException("Incident title cannot be blank");
        }
        if (input.severity() == null || input.severity().isBlank()) {
            throw new IllegalArgumentException("Incident severity cannot be blank");
        }

        return graphRepository.createOrEnsureIncident(graphName, input);
    }

    @Override
    public ResolutionRecord confirmResolution(String graphName, String incidentId, ResolutionInput input) {
        Objects.requireNonNull(graphName, "graphName cannot be null");
        Objects.requireNonNull(incidentId, "incidentId cannot be null");
        Objects.requireNonNull(input, "input cannot be null");

        // 1. Input field validation
        validateResolutionInput(incidentId, input);

        // 2. Incident existence check
        Optional<IncidentRecord> incidentOpt = graphRepository.findIncidentById(graphName, incidentId);
        if (incidentOpt.isEmpty()) {
            throw new IncidentNotFoundException("Incident not found with ID: " + incidentId);
        }

        IncidentRecord incident = incidentOpt.get();

        // 3. Status and idempotency check
        if ("RESOLVED".equalsIgnoreCase(incident.status())) {
            Optional<ResolutionRecord> existingResOpt = graphRepository.findResolutionByIncidentId(graphName, incidentId);
            if (existingResOpt.isPresent()) {
                ResolutionRecord existingRes = existingResOpt.get();
                boolean isIdentical = Objects.equals(existingRes.title(), input.title())
                        && Objects.equals(existingRes.actionTaken(), input.actionTaken())
                        && Objects.equals(existingRes.remediationType(), input.remediationType());

                if (isIdentical) {
                    log.info("Idempotent resolution confirmation for already resolved incident: {}", incidentId);
                    return existingRes;
                } else {
                    throw new ResolutionConflictException(
                            "Incident " + incidentId + " is already resolved with differing resolution: " + existingRes.id()
                    );
                }
            } else {
                throw new IncidentAlreadyResolvedException("Incident " + incidentId + " is already marked RESOLVED.");
            }
        }

        // 4. Validate root cause service exists in FalkorDB
        if (!graphRepository.serviceExists(graphName, input.rootCauseServiceId())) {
            throw new ServiceNotFoundException("Root cause service not found in graph: " + input.rootCauseServiceId());
        }

        // 5. Validate optional triggering deployment exists if specified
        if (input.triggeringDeploymentId() != null && !input.triggeringDeploymentId().isBlank()) {
            if (!graphRepository.deploymentExists(graphName, input.triggeringDeploymentId())) {
                throw new DeploymentNotFoundException(
                        "Triggering deployment not found in graph: " + input.triggeringDeploymentId()
                );
            }
        }

        // 6. Server-controlled timestamp (never accept client timestamp)
        long confirmedAt = clock.millis();

        // 7. Atomic persistence in FalkorDB
        ResolutionRecord confirmed = graphRepository.confirmResolution(graphName, incidentId, input, confirmedAt);
        log.info("Confirmed resolution {} for incident {} (rootCause: {})", confirmed.id(), incidentId, input.rootCauseServiceId());
        return confirmed;
    }

    private void validateResolutionInput(String incidentId, ResolutionInput input) {
        if (incidentId.isBlank()) {
            throw new InvalidResolutionInputException("incidentId cannot be blank");
        }
        if (input.rootCauseServiceId() == null || input.rootCauseServiceId().isBlank()) {
            throw new InvalidResolutionInputException("rootCauseServiceId cannot be blank");
        }
        if (input.title() == null || input.title().isBlank()) {
            throw new InvalidResolutionInputException("title / summary cannot be blank");
        }
        if (input.title().length() > MAX_TITLE_LENGTH) {
            throw new InvalidResolutionInputException("title exceeds maximum length of " + MAX_TITLE_LENGTH + " characters");
        }
        if (input.actionTaken() == null || input.actionTaken().isBlank()) {
            throw new InvalidResolutionInputException("actionTaken cannot be blank");
        }
        if (input.actionTaken().length() > MAX_ACTION_TAKEN_LENGTH) {
            throw new InvalidResolutionInputException("actionTaken exceeds maximum length of " + MAX_ACTION_TAKEN_LENGTH + " characters");
        }
        if (input.confirmedBy() == null || input.confirmedBy().isBlank()) {
            throw new InvalidResolutionInputException("confirmedBy cannot be blank");
        }
        if (input.confirmedBy().length() > MAX_CONFIRMED_BY_LENGTH) {
            throw new InvalidResolutionInputException("confirmedBy exceeds maximum length of " + MAX_CONFIRMED_BY_LENGTH + " characters");
        }
        if (input.remediationType() == null || input.remediationType().isBlank()) {
            throw new InvalidResolutionInputException("remediationType cannot be blank");
        }
        if (input.remediationType().length() > MAX_REMEDIATION_TYPE_LENGTH) {
            throw new InvalidResolutionInputException("remediationType exceeds maximum length of " + MAX_REMEDIATION_TYPE_LENGTH + " characters");
        }
    }
}
