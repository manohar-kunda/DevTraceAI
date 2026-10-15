package com.devtrace.service;

import com.devtrace.model.IncidentInput;
import com.devtrace.model.IncidentRecord;
import com.devtrace.model.ResolutionInput;
import com.devtrace.model.ResolutionRecord;

/**
 * Service for incident tracking and user-confirmed resolution write-back in FalkorDB.
 * Enforces server-controlled timestamps, schema validation, and strict idempotency.
 * Engine hypotheses are NEVER persisted by this service; only human user confirmations.
 */
public interface ResolutionService {

    /**
     * Idempotently creates or updates an incident node in FalkorDB.
     */
    IncidentRecord createOrEnsureIncident(String graphName, IncidentInput input);

    /**
     * Confirms an incident resolution and writes it back to FalkorDB.
     * Marks the incident as RESOLVED, creates a Resolution node with source 'user-confirmed',
     * links [:RESOLVES], [:ADDRESSES], and optional [:TRIGGERED_BY].
     *
     * @param graphName  Target graph.
     * @param incidentId Incident ID.
     * @param input      Validated resolution input.
     * @return Persisted ResolutionRecord with server-generated metadata.
     */
    ResolutionRecord confirmResolution(String graphName, String incidentId, ResolutionInput input);
}
