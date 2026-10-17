package com.devtrace.controller;

import com.devtrace.dto.GoldenIncidentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private static final long T0 = 1792058400000L; // 2026-10-15 10:00:00 UTC

    @GetMapping("/golden-incident")
    public ResponseEntity<GoldenIncidentResponse> getGoldenIncident() {
        return ResponseEntity.ok(new GoldenIncidentResponse(
                true,
                "inc-2026-10-15-checkout-001",
                "Checkout requests are failing after the latest deployment and payment timeouts have increased",
                null,
                null,
                T0
        ));
    }
}
