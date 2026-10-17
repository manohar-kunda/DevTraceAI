package com.devtrace.controller;

import com.devtrace.config.DevTraceTokenInterceptor;
import com.devtrace.dto.*;
import com.devtrace.exception.*;
import com.devtrace.model.*;
import com.devtrace.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import redis.clients.jedis.exceptions.JedisConnectionException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class IncidentControllerTest {

    private static final String TEST_TOKEN = "test-secret-token-12345";
    private static final String GRAPH_NAME = "devtrace";

    private IncidentService incidentService;
    private MockMvc mockMvc;
    private MockMvc mockMvcUnsetToken;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        incidentService = Mockito.mock(IncidentService.class);
        objectMapper = new ObjectMapper();

        IncidentController incidentController = new IncidentController(incidentService, GRAPH_NAME);
        ServiceController serviceController = new ServiceController(incidentService, GRAPH_NAME);
        DemoController demoController = new DemoController();
        GlobalExceptionHandler exceptionHandler = new GlobalExceptionHandler();

        DevTraceTokenInterceptor tokenInterceptor = new DevTraceTokenInterceptor(TEST_TOKEN);
        mockMvc = MockMvcBuilders.standaloneSetup(incidentController, serviceController, demoController)
                .setControllerAdvice(exceptionHandler)
                .addInterceptors(tokenInterceptor)
                .build();

        DevTraceTokenInterceptor unsetTokenInterceptor = new DevTraceTokenInterceptor("");
        mockMvcUnsetToken = MockMvcBuilders.standaloneSetup(incidentController, serviceController, demoController)
                .setControllerAdvice(exceptionHandler)
                .addInterceptors(unsetTokenInterceptor)
                .build();
    }

    @Test
    @DisplayName("POST /api/incidents - 201 Created on new incident creation")
    void testCreateIncidentSuccess() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "Payment timeouts surging", "payment-service", "err-sig-timeout", 1792058400000L, "inc-001"
        );
        CreateIncidentResponse response = new CreateIncidentResponse("inc-001", "Payment timeouts surging", "OPEN", 1792058400000L, "P1", true);

        when(incidentService.createIncident(eq(GRAPH_NAME), any(CreateIncidentRequest.class)))
                .thenReturn(new IncidentService.IncidentCreationResult(response, true));

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentId").value("inc-001"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.created").value(true));
    }

    @Test
    @DisplayName("POST /api/incidents - 200 OK for idempotent repeat with identical payload")
    void testCreateIncidentIdempotentRepeat() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest(
                "Payment timeouts surging", "payment-service", "err-sig-timeout", 1792058400000L, "inc-001"
        );
        CreateIncidentResponse response = new CreateIncidentResponse("inc-001", "Payment timeouts surging", "OPEN", 1792058400000L, "P1", false);

        when(incidentService.createIncident(eq(GRAPH_NAME), any(CreateIncidentRequest.class)))
                .thenReturn(new IncidentService.IncidentCreationResult(response, false));

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("inc-001"))
                .andExpect(jsonPath("$.created").value(false));
    }

    @Test
    @DisplayName("POST /api/incidents - 409 Conflict when incidentId exists with differing payload")
    void testCreateIncidentConflict() throws Exception {
        CreateIncidentRequest request = new CreateIncidentRequest("Different description entirely", null, null, null, "inc-001");

        when(incidentService.createIncident(eq(GRAPH_NAME), any(CreateIncidentRequest.class)))
                .thenThrow(new IncidentConflictException("Incident inc-001 already exists with differing details"));

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Incident inc-001 already exists with differing details"));
    }

    @Test
    @DisplayName("POST /api/incidents - 400 Bad Request on validation failure (blank description)")
    void testCreateIncidentValidationFailure() throws Exception {
        CreateIncidentRequest invalidRequest = new CreateIncidentRequest("", null, null, null, "inc-001");

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors.description").exists());
    }

    @Test
    @DisplayName("GET /api/incidents/{id} - 200 OK returning incident and confirmed resolution")
    void testGetIncidentSuccess() throws Exception {
        ResolutionRecord res = new ResolutionRecord(
                "res-001", "Fix pool size", "CONFIG_CHANGE", "Set pool to 50", 1792065600000L, "sre-alice", "user-confirmed"
        );
        IncidentStatusResponse response = new IncidentStatusResponse("inc-001", "Outage", "RESOLVED", 1792058400000L, "P1", res);

        when(incidentService.getIncident(eq(GRAPH_NAME), eq("inc-001"))).thenReturn(response);

        mockMvc.perform(get("/api/incidents/inc-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("inc-001"))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.confirmedResolution.id").value("res-001"))
                .andExpect(jsonPath("$.confirmedResolution.source").value("user-confirmed"));
    }

    @Test
    @DisplayName("GET /api/incidents/{id} - 404 Not Found for unknown incident")
    void testGetIncidentNotFound() throws Exception {
        when(incidentService.getIncident(eq(GRAPH_NAME), eq("inc-unknown")))
                .thenThrow(new IncidentNotFoundException("Incident not found with ID: inc-unknown"));

        mockMvc.perform(get("/api/incidents/inc-unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Incident not found with ID: inc-unknown"));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/investigation - 200 OK read-only investigation")
    void testInvestigateIncidentSuccess() throws Exception {
        InvestigationReport mockReport = new InvestigationReport(
                "HYPOTHESES_RANKED",
                new InvestigationRequest("desc"),
                new EntryPointsResult(java.util.Optional.empty(), java.util.Optional.empty()),
                new ObservedFacts(List.of(), List.of(), List.of()),
                new GraphRelationships(Map.of(), new DependentsResult("s", List.of(), List.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );

        when(incidentService.investigateIncident(eq(GRAPH_NAME), eq("inc-001"), any()))
                .thenReturn(mockReport);

        mockMvc.perform(post("/api/incidents/inc-001/investigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HYPOTHESES_RANKED"));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/investigation - 503 Service Unavailable when database is down")
    void testInvestigateIncidentDatabaseUnavailable() throws Exception {
        when(incidentService.investigateIncident(eq(GRAPH_NAME), eq("inc-001"), any()))
                .thenThrow(new JedisConnectionException("Failed to connect to 127.0.0.1:6379"));

        mockMvc.perform(post("/api/incidents/inc-001/investigation"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.title").value("Service Unavailable"))
                .andExpect(jsonPath("$.detail").value("Database service is temporarily unavailable. Please verify FalkorDB is running and accessible."));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/resolution - 401 Unauthorized when X-DevTrace-Token header is missing")
    void testConfirmResolutionMissingToken() throws Exception {
        ConfirmResolutionRequest request = new ConfirmResolutionRequest(
                "service-payment", "Pool fix", "CONFIG_CHANGE", "Action", "sre"
        );

        mockMvc.perform(post("/api/incidents/inc-001/resolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Missing required 'X-DevTrace-Token' authentication header."));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/resolution - 401 Unauthorized when token is wrong")
    void testConfirmResolutionWrongToken() throws Exception {
        ConfirmResolutionRequest request = new ConfirmResolutionRequest(
                "service-payment", "Pool fix", "CONFIG_CHANGE", "Action", "sre"
        );

        mockMvc.perform(post("/api/incidents/inc-001/resolution")
                        .header("X-DevTrace-Token", "wrong-token-value")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Invalid authentication token provided in 'X-DevTrace-Token' header."));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/resolution - 401 Unauthorized fail-closed when server DEVTRACE_API_TOKEN is unset")
    void testConfirmResolutionUnsetServerTokenFailClosed() throws Exception {
        ConfirmResolutionRequest request = new ConfirmResolutionRequest(
                "service-payment", "Pool fix", "CONFIG_CHANGE", "Action", "sre"
        );

        mockMvcUnsetToken.perform(post("/api/incidents/inc-001/resolution")
                        .header("X-DevTrace-Token", TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("The resolution confirmation endpoint is disabled because DEVTRACE_API_TOKEN is not configured on the server."));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/resolution - 201 Created on valid token and successful confirmation")
    void testConfirmResolutionSuccess() throws Exception {
        ConfirmResolutionRequest request = new ConfirmResolutionRequest(
                "service-payment", "Pool fix", "CONFIG_CHANGE", "Action", "sre"
        );
        ResolutionRecord record = new ResolutionRecord(
                "res-inc-001", "Pool fix", "CONFIG_CHANGE", "Action", 1792065600000L, "sre", "user-confirmed"
        );

        when(incidentService.confirmResolution(eq(GRAPH_NAME), eq("inc-001"), any(ConfirmResolutionRequest.class)))
                .thenReturn(new IncidentService.ResolutionConfirmationResult(record, true));

        mockMvc.perform(post("/api/incidents/inc-001/resolution")
                        .header("X-DevTrace-Token", TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("res-inc-001"))
                .andExpect(jsonPath("$.source").value("user-confirmed"));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/resolution - 200 OK on identical repeat confirmation")
    void testConfirmResolutionIdenticalRepeat() throws Exception {
        ConfirmResolutionRequest request = new ConfirmResolutionRequest(
                "service-payment", "Pool fix", "CONFIG_CHANGE", "Action", "sre"
        );
        ResolutionRecord record = new ResolutionRecord(
                "res-inc-001", "Pool fix", "CONFIG_CHANGE", "Action", 1792065600000L, "sre", "user-confirmed"
        );

        when(incidentService.confirmResolution(eq(GRAPH_NAME), eq("inc-001"), any(ConfirmResolutionRequest.class)))
                .thenReturn(new IncidentService.ResolutionConfirmationResult(record, false));

        mockMvc.perform(post("/api/incidents/inc-001/resolution")
                        .header("X-DevTrace-Token", TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("res-inc-001"));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/resolution - 409 Conflict on conflicting resolution payload")
    void testConfirmResolutionConflict() throws Exception {
        ConfirmResolutionRequest request = new ConfirmResolutionRequest(
                "service-payment", "Conflicting fix", "RESTART", "Action", "sre"
        );

        when(incidentService.confirmResolution(eq(GRAPH_NAME), eq("inc-001"), any(ConfirmResolutionRequest.class)))
                .thenThrow(new ResolutionConflictException("Incident inc-001 is already resolved with differing resolution"));

        mockMvc.perform(post("/api/incidents/inc-001/resolution")
                        .header("X-DevTrace-Token", TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail").value("Incident inc-001 is already resolved with differing resolution"));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/resolution - 404 Not Found on unknown service")
    void testConfirmResolutionUnknownService() throws Exception {
        ConfirmResolutionRequest request = new ConfirmResolutionRequest(
                "service-ghost", "Fix", "CONFIG_CHANGE", "Action", "sre"
        );

        when(incidentService.confirmResolution(eq(GRAPH_NAME), eq("inc-001"), any(ConfirmResolutionRequest.class)))
                .thenThrow(new ServiceNotFoundException("Root cause service not found in graph: service-ghost"));

        mockMvc.perform(post("/api/incidents/inc-001/resolution")
                        .header("X-DevTrace-Token", TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Root cause service not found in graph: service-ghost"));
    }

    @Test
    @DisplayName("POST /api/incidents/{id}/resolution - 400 Bad Request on blank validation field")
    void testConfirmResolutionValidationFailure() throws Exception {
        ConfirmResolutionRequest invalidRequest = new ConfirmResolutionRequest(
                "", "", "CONFIG_CHANGE", "Action", "sre"
        );

        mockMvc.perform(post("/api/incidents/inc-001/resolution")
                        .header("X-DevTrace-Token", TEST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.rootCauseServiceId").exists())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
    }

    @Test
    @DisplayName("GET /api/services/{id}/blast-radius - 200 OK with server-side capped depth")
    void testGetBlastRadiusSuccess() throws Exception {
        BlastRadiusResponse response = new BlastRadiusResponse(
                "service-payment",
                List.of(new ServiceHop("service-order", "order-service", "MICROSERVICE", 1)),
                List.of(new ServiceHop("service-checkout", "checkout-service", "MICROSERVICE", 2)),
                2
        );

        when(incidentService.getBlastRadius(eq(GRAPH_NAME), eq("service-payment"), anyInt()))
                .thenReturn(response);

        mockMvc.perform(get("/api/services/service-payment/blast-radius?maxDepth=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetServiceId").value("service-payment"))
                .andExpect(jsonPath("$.totalDependents").value(2));
    }

    @Test
    @DisplayName("GET /api/services/{id}/blast-radius - 404 Not Found on unknown service")
    void testGetBlastRadiusNotFound() throws Exception {
        when(incidentService.getBlastRadius(eq(GRAPH_NAME), eq("service-ghost"), anyInt()))
                .thenThrow(new ServiceNotFoundException("Service not found in graph: service-ghost"));

        mockMvc.perform(get("/api/services/service-ghost/blast-radius"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Service not found in graph: service-ghost"));
    }

    @Test
    @DisplayName("GET /api/demo/golden-incident - 200 OK returns golden incident marked demo: true")
    void testGetGoldenIncident() throws Exception {
        mockMvc.perform(get("/api/demo/golden-incident"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.incidentId").value("inc-2026-10-15-checkout-001"))
                .andExpect(jsonPath("$.serviceHint").value("checkout-service"))
                .andExpect(jsonPath("$.errorHint").value("err-sig-payment-pool-timeout"))
                .andExpect(jsonPath("$.startedAt").value(1792058400000L));
    }
}
