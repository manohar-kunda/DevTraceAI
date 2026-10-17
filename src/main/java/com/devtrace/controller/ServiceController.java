package com.devtrace.controller;

import com.devtrace.dto.BlastRadiusResponse;
import com.devtrace.service.IncidentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/services")
public class ServiceController {

    private final IncidentService incidentService;
    private final String graphName;

    public ServiceController(
            IncidentService incidentService,
            @Value("${devtrace.falkordb.graph:devtrace}") String graphName
    ) {
        this.incidentService = incidentService;
        this.graphName = graphName;
    }

    @GetMapping("/{id}/blast-radius")
    public ResponseEntity<BlastRadiusResponse> getBlastRadius(
            @PathVariable("id") String id,
            @RequestParam(name = "maxDepth", defaultValue = "3") int maxDepth
    ) {
        BlastRadiusResponse response = incidentService.getBlastRadius(graphName, id, maxDepth);
        return ResponseEntity.ok(response);
    }
}
