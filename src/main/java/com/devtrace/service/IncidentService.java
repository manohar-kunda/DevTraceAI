package com.devtrace.service;

import com.devtrace.dto.*;
import com.devtrace.model.InvestigationReport;
import com.devtrace.model.InvestigationRequest;
import com.devtrace.model.ResolutionRecord;

public interface IncidentService {

    record IncidentCreationResult(CreateIncidentResponse response, boolean created) {}

    IncidentCreationResult createIncident(String graphName, CreateIncidentRequest request);

    IncidentStatusResponse getIncident(String graphName, String incidentId);

    InvestigationReport investigateIncident(String graphName, String incidentId, InvestigationRequest overrideRequest);

    BlastRadiusResponse getBlastRadius(String graphName, String serviceId, int maxDepth);

    record ResolutionConfirmationResult(ResolutionRecord record, boolean created) {}

    ResolutionConfirmationResult confirmResolution(String graphName, String incidentId, ConfirmResolutionRequest request);
}
