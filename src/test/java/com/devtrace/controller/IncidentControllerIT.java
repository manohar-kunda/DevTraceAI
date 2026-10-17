package com.devtrace.controller;

import com.devtrace.config.EnvLoader;
import com.devtrace.dto.ConfirmResolutionRequest;
import com.devtrace.dto.CreateIncidentRequest;
import com.devtrace.seed.FalkorSchemaBootstrap;
import com.devtrace.seed.FalkorSeedService;
import com.falkordb.Driver;
import com.falkordb.FalkorDB;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = {
        "devtrace.falkordb.graph=devtrace_test",
        "DEVTRACE_API_TOKEN=test-it-token-999"
})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IncidentControllerIT {

    private static final String TEST_GRAPH = "devtrace_test";
    private static final String IT_TOKEN = "test-it-token-999";
    private static final long T0 = 1792058400000L; // 2026-10-15 10:00:00 UTC

    private static Driver driver;
    private static FalkorSeedService seedService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @BeforeAll
    static void setUpAll() {
        EnvLoader.loadLocalEnv();
        String host = System.getProperty("FALKORDB_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getProperty("FALKORDB_PORT", "6379"));
        String password = System.getProperty("FALKORDB_PASSWORD");

        driver = FalkorDB.builder()
                .host(host)
                .port(port)
                .credentials("default", password)
                .build();

        FalkorSchemaBootstrap bootstrap = new FalkorSchemaBootstrap(driver);
        seedService = new FalkorSeedService(driver, bootstrap);
        seedService.seed(TEST_GRAPH, true);
    }

    @AfterAll
    static void tearDownAll() {
        if (driver != null) {
            try {
                driver.graph(TEST_GRAPH).deleteGraph();
            } catch (Exception ignored) {
            }
            try {
                driver.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("HTTP End-to-End: Full incident lifecycle flow across REST endpoints")
    void testFullIncidentLifecycleFlow() throws Exception {
        String incident1Id = "inc-2026-10-15-checkout-001";

        // Step 1: Create incident -> 201 Created
        CreateIncidentRequest createReq = new CreateIncidentRequest(
                "Checkout requests are failing after the latest deployment and payment timeouts have increased",
                "checkout-service",
                "err-sig-payment-pool-timeout",
                T0 - 1800000L,
                incident1Id
        );

        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.incidentId").value(incident1Id))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.created").value(true));

        // Step 2: Investigate incident -> 200 OK (read-only)
        mockMvc.perform(post("/api/incidents/" + incident1Id + "/investigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HYPOTHESES_RANKED"))
                .andExpect(jsonPath("$.hypotheses[0].serviceId").value("service-payment"))
                .andExpect(jsonPath("$.hypotheses[0].tier").value("STRONG"));

        // Step 3: Confirm resolution WITHOUT token -> 401 Unauthorized
        ConfirmResolutionRequest resReq = new ConfirmResolutionRequest(
                "service-payment",
                "Reverted HikariCP connection pool constraints via hotfix config deploy",
                "CONFIG_CHANGE",
                "Patched ConfigMap to set maximumPoolSize=40, connectionTimeout=25000ms, and restarted payment-service pods to restore connection pool headroom.",
                "sre-alice",
                "dep-payment-v2.4.1"
        );

        mockMvc.perform(post("/api/incidents/" + incident1Id + "/resolution")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value(containsString("Missing required 'X-DevTrace-Token'")));

        // Step 4: Confirm resolution WITH token -> 201 Created
        mockMvc.perform(post("/api/incidents/" + incident1Id + "/resolution")
                        .header("X-DevTrace-Token", IT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("res-" + incident1Id))
                .andExpect(jsonPath("$.source").value("user-confirmed"))
                .andExpect(jsonPath("$.confirmedBy").value("sre-alice"));

        // Step 5: Repeat identical confirmation -> 200 OK
        mockMvc.perform(post("/api/incidents/" + incident1Id + "/resolution")
                        .header("X-DevTrace-Token", IT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("res-" + incident1Id));

        // Step 6: Conflicting payload confirmation -> 409 Conflict
        ConfirmResolutionRequest conflictReq = new ConfirmResolutionRequest(
                "service-payment",
                "Totally different remediation title",
                "RESTART",
                "Restarted payment service pods",
                "sre-bob"
        );
        mockMvc.perform(post("/api/incidents/" + incident1Id + "/resolution")
                        .header("X-DevTrace-Token", IT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"));

        // Step 7: Unknown incident resolution confirmation -> 404 Not Found
        mockMvc.perform(post("/api/incidents/inc-ghost-999/resolution")
                        .header("X-DevTrace-Token", IT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resReq)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        // Step 8: Invalid body (validation failure) -> 400 Bad Request
        ConfirmResolutionRequest invalidBodyReq = new ConfirmResolutionRequest(
                "", "", "CONFIG_CHANGE", "Action", "sre"
        );
        mockMvc.perform(post("/api/incidents/" + incident1Id + "/resolution")
                        .header("X-DevTrace-Token", IT_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidBodyReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // Step 9: Investigate a SECOND incident and show both confirmed resolutions
        String incident2Id = "inc-2026-10-15-checkout-002";
        CreateIncidentRequest createReq2 = new CreateIncidentRequest(
                "Orders timing out during payment gateway handoff with connection pool exhaustion",
                "checkout-service",
                "err-sig-payment-pool-timeout",
                T0 + 9000000L,
                incident2Id
        );
        mockMvc.perform(post("/api/incidents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq2)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/incidents/" + incident2Id + "/investigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HYPOTHESES_RANKED"))
                .andExpect(jsonPath("$.observedFacts.matchedHistoricalIncidents", hasSize(2)))
                .andExpect(jsonPath("$.observedFacts.matchedHistoricalIncidents[0].incidentId").value(incident1Id))
                .andExpect(jsonPath("$.observedFacts.matchedHistoricalIncidents[0].confirmedResolution.source").value("user-confirmed"))
                .andExpect(jsonPath("$.observedFacts.matchedHistoricalIncidents[1].incidentId").value("inc-2026-08-12-pay"))
                .andExpect(jsonPath("$.observedFacts.matchedHistoricalIncidents[1].confirmedResolution.source").value("synthetic"))
                .andExpect(jsonPath("$.suggestedNextSteps[0].sourceResolutionId").value("res-" + incident1Id))
                .andExpect(jsonPath("$.suggestedNextSteps[1].sourceResolutionId").value("res-pay-timeout-fix"));
    }

    @Test
    @Order(2)
    @DisplayName("GET /api/incidents/{id} returns RESOLVED status and full confirmed resolution record")
    void testGetResolvedIncident() throws Exception {
        mockMvc.perform(get("/api/incidents/inc-2026-10-15-checkout-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("inc-2026-10-15-checkout-001"))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.confirmedResolution.id").value("res-inc-2026-10-15-checkout-001"))
                .andExpect(jsonPath("$.confirmedResolution.confirmedBy").value("sre-alice"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /api/services/{id}/blast-radius returns callers bounded by depth")
    void testGetBlastRadiusEndpoint() throws Exception {
        mockMvc.perform(get("/api/services/service-payment/blast-radius?maxDepth=4"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetServiceId").value("service-payment"))
                .andExpect(jsonPath("$.directDependents", hasSize(1)))
                .andExpect(jsonPath("$.directDependents[0].serviceId").value("service-order"))
                .andExpect(jsonPath("$.totalDependents").value(3));
    }

    @Test
    @Order(4)
    @DisplayName("GET /api/demo/golden-incident returns golden scenario with demo: true")
    void testGetDemoGoldenIncident() throws Exception {
        mockMvc.perform(get("/api/demo/golden-incident"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.incidentId").value("inc-2026-10-15-checkout-001"))
                .andExpect(jsonPath("$.serviceHint").value("checkout-service"))
                .andExpect(jsonPath("$.errorHint").value("err-sig-payment-pool-timeout"))
                .andExpect(jsonPath("$.startedAt").value(T0));
    }
}
