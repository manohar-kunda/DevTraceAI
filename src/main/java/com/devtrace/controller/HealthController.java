package com.devtrace.controller;

import com.devtrace.config.FalkorDbProperties;
import com.falkordb.Driver;
import com.falkordb.Graph;
import com.falkordb.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    private static final Logger log = LoggerFactory.getLogger(HealthController.class);

    private final Driver falkorDriver;
    private final FalkorDbProperties properties;

    public HealthController(Driver falkorDriver, FalkorDbProperties properties) {
        this.falkorDriver = falkorDriver;
        this.properties = properties;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> response = new LinkedHashMap<>();

        try {
            Graph graph = falkorDriver.graph(properties.getGraph());
            ResultSet result = graph.query("RETURN 1 AS ping");

            Map<String, Object> dbDetails = new LinkedHashMap<>();
            dbDetails.put("status", "UP");
            dbDetails.put("graph", properties.getGraph());
            dbDetails.put("details", "FalkorDB query executed successfully");

            response.put("status", "UP");
            response.put("database", dbDetails);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("FalkorDB health probe failed: {}", e.getMessage());

            Map<String, Object> dbDetails = new LinkedHashMap<>();
            dbDetails.put("status", "DOWN");
            dbDetails.put("error", "FalkorDB is unreachable. Verify that the database container is running and credentials are valid.");

            response.put("status", "DOWN");
            response.put("database", dbDetails);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response);
        }
    }
}
