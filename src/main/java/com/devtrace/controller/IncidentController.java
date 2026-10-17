package com.devtrace.controller;

import com.devtrace.dto.ConfirmResolutionRequest;
import com.devtrace.dto.CreateIncidentRequest;
import com.devtrace.dto.IncidentStatusResponse;
import com.devtrace.model.InvestigationReport;
import com.devtrace.model.InvestigationRequest;
import com.devtrace.model.ResolutionRecord;
import com.devtrace.service.IncidentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;
    private final String graphName;

    public IncidentController(
            IncidentService incidentService,
            @Value("${devtrace.falkordb.graph:devtrace}") String graphName
    ) {
        this.incidentService = incidentService;
        this.graphName = graphName;
    }

    @PostMapping
    public ResponseEntity<?> createIncident(@Valid @RequestBody CreateIncidentRequest request) {
        IncidentService.IncidentCreationResult result = incidentService.createIncident(graphName, request);
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.response());
        } else {
            return ResponseEntity.ok(result.response());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncidentStatusResponse> getIncident(@PathVariable("id") String id) {
        IncidentStatusResponse response = incidentService.getIncident(graphName, id);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/investigation")
    public ResponseEntity<InvestigationReport> investigateIncident(
            @PathVariable("id") String id,
            @RequestBody(required = false) InvestigationRequest request
    ) {
        InvestigationReport report = incidentService.investigateIncident(graphName, id, request);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/{id}/resolution")
    public ResponseEntity<ResolutionRecord> confirmResolution(
            @PathVariable("id") String id,
            @Valid @RequestBody ConfirmResolutionRequest request
    ) {
        IncidentService.ResolutionConfirmationResult result = incidentService.confirmResolution(graphName, id, request);
        if (result.created()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(result.record());
        } else {
            return ResponseEntity.ok(result.record());
        }
    }
}
