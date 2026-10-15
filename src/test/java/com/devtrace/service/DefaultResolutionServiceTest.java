package com.devtrace.service;

import com.devtrace.exception.*;
import com.devtrace.model.IncidentInput;
import com.devtrace.model.IncidentRecord;
import com.devtrace.model.ResolutionInput;
import com.devtrace.model.ResolutionRecord;
import com.devtrace.repository.GraphRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultResolutionServiceTest {

    private GraphRepository graphRepository;
    private Clock fixedClock;
    private DefaultResolutionService resolutionService;

    private static final long T0 = 1792058400000L; // 2026-10-15 10:00:00 UTC
    private static final String GRAPH = "devtrace_test";

    @BeforeEach
    void setUp() {
        graphRepository = Mockito.mock(GraphRepository.class);
        fixedClock = Clock.fixed(Instant.ofEpochMilli(T0), ZoneOffset.UTC);
        resolutionService = new DefaultResolutionService(graphRepository, fixedClock);
    }

    @Test
    @DisplayName("confirmResolution: successfully marks incident RESOLVED and returns record with server clock and user-confirmed source")
    void testConfirmResolutionSuccess() {
        String incidentId = "inc-2026-10-15-001";
        IncidentRecord incident = new IncidentRecord(incidentId, "Checkout failure", "OPEN", T0 - 3600000, "P1");

        when(graphRepository.findIncidentById(eq(GRAPH), eq(incidentId))).thenReturn(Optional.of(incident));
        when(graphRepository.serviceExists(eq(GRAPH), eq("service-payment"))).thenReturn(true);
        when(graphRepository.deploymentExists(eq(GRAPH), eq("dep-payment-v2.4.1"))).thenReturn(true);

        ResolutionInput input = new ResolutionInput(
                "service-payment",
                "Increase HikariCP connection pool",
                "CONFIG_CHANGE",
                "Increased maximumPoolSize to 50 on postgres-payments datasource",
                "sre-alice",
                "dep-payment-v2.4.1"
        );

        ResolutionRecord expected = new ResolutionRecord(
                "res-" + incidentId,
                input.title(),
                input.remediationType(),
                input.actionTaken(),
                T0,
                input.confirmedBy(),
                "user-confirmed"
        );

        when(graphRepository.confirmResolution(eq(GRAPH), eq(incidentId), eq(input), eq(T0)))
                .thenReturn(expected);

        ResolutionRecord actual = resolutionService.confirmResolution(GRAPH, incidentId, input);

        assertNotNull(actual);
        assertEquals("res-" + incidentId, actual.id());
        assertEquals(T0, actual.confirmedAt(), "Timestamp must come strictly from server Clock");
        assertEquals("user-confirmed", actual.source());
        assertEquals("sre-alice", actual.confirmedBy());

        verify(graphRepository).confirmResolution(eq(GRAPH), eq(incidentId), eq(input), eq(T0));
    }

    @Test
    @DisplayName("confirmResolution: throws IncidentNotFoundException when incident does not exist in graph")
    void testConfirmResolutionIncidentNotFound() {
        when(graphRepository.findIncidentById(eq(GRAPH), eq("inc-unknown"))).thenReturn(Optional.empty());

        ResolutionInput input = new ResolutionInput("service-payment", "Fix", "CONFIG_CHANGE", "Action taken", "sre");

        assertThrows(IncidentNotFoundException.class, () ->
                resolutionService.confirmResolution(GRAPH, "inc-unknown", input)
        );
    }

    @Test
    @DisplayName("confirmResolution: idempotent call with identical payload returns existing resolution")
    void testConfirmResolutionIdempotentSuccess() {
        String incidentId = "inc-2026-10-15-001";
        IncidentRecord incident = new IncidentRecord(incidentId, "Checkout failure", "RESOLVED", T0 - 3600000, "P1");

        ResolutionRecord existing = new ResolutionRecord(
                "res-" + incidentId,
                "Increase pool",
                "CONFIG_CHANGE",
                "Increased pool size",
                T0 - 60000,
                "sre-alice",
                "user-confirmed"
        );

        when(graphRepository.findIncidentById(eq(GRAPH), eq(incidentId))).thenReturn(Optional.of(incident));
        when(graphRepository.findResolutionByIncidentId(eq(GRAPH), eq(incidentId))).thenReturn(Optional.of(existing));

        ResolutionInput input = new ResolutionInput(
                "service-payment",
                "Increase pool",
                "CONFIG_CHANGE",
                "Increased pool size",
                "sre-alice"
        );

        ResolutionRecord actual = resolutionService.confirmResolution(GRAPH, incidentId, input);

        assertEquals(existing.id(), actual.id());
        assertEquals(existing.confirmedAt(), actual.confirmedAt());
        verify(graphRepository, never()).confirmResolution(any(), any(), any(), anyLong());
    }

    @Test
    @DisplayName("confirmResolution: conflicting payload on already resolved incident throws ResolutionConflictException")
    void testConfirmResolutionConflictThrows() {
        String incidentId = "inc-2026-10-15-001";
        IncidentRecord incident = new IncidentRecord(incidentId, "Checkout failure", "RESOLVED", T0 - 3600000, "P1");

        ResolutionRecord existing = new ResolutionRecord(
                "res-" + incidentId,
                "Increase pool",
                "CONFIG_CHANGE",
                "Increased pool size",
                T0 - 60000,
                "sre-alice",
                "user-confirmed"
        );

        when(graphRepository.findIncidentById(eq(GRAPH), eq(incidentId))).thenReturn(Optional.of(incident));
        when(graphRepository.findResolutionByIncidentId(eq(GRAPH), eq(incidentId))).thenReturn(Optional.of(existing));

        // Different title/action
        ResolutionInput differingInput = new ResolutionInput(
                "service-payment",
                "Different fix",
                "RESTART",
                "Restarted service",
                "sre-bob"
        );

        assertThrows(ResolutionConflictException.class, () ->
                resolutionService.confirmResolution(GRAPH, incidentId, differingInput)
        );
    }

    @Test
    @DisplayName("confirmResolution: unknown root cause service ID throws ServiceNotFoundException")
    void testConfirmResolutionUnknownServiceThrows() {
        String incidentId = "inc-2026-10-15-001";
        IncidentRecord incident = new IncidentRecord(incidentId, "Checkout failure", "OPEN", T0 - 3600000, "P1");

        when(graphRepository.findIncidentById(eq(GRAPH), eq(incidentId))).thenReturn(Optional.of(incident));
        when(graphRepository.serviceExists(eq(GRAPH), eq("service-ghost"))).thenReturn(false);

        ResolutionInput input = new ResolutionInput("service-ghost", "Title", "CONFIG_CHANGE", "Action", "sre");

        assertThrows(ServiceNotFoundException.class, () ->
                resolutionService.confirmResolution(GRAPH, incidentId, input)
        );
    }

    @Test
    @DisplayName("confirmResolution: unknown triggering deployment throws DeploymentNotFoundException")
    void testConfirmResolutionUnknownDeploymentThrows() {
        String incidentId = "inc-2026-10-15-001";
        IncidentRecord incident = new IncidentRecord(incidentId, "Checkout failure", "OPEN", T0 - 3600000, "P1");

        when(graphRepository.findIncidentById(eq(GRAPH), eq(incidentId))).thenReturn(Optional.of(incident));
        when(graphRepository.serviceExists(eq(GRAPH), eq("service-payment"))).thenReturn(true);
        when(graphRepository.deploymentExists(eq(GRAPH), eq("dep-ghost"))).thenReturn(false);

        ResolutionInput input = new ResolutionInput("service-payment", "Title", "CONFIG_CHANGE", "Action", "sre", "dep-ghost");

        assertThrows(DeploymentNotFoundException.class, () ->
                resolutionService.confirmResolution(GRAPH, incidentId, input)
        );
    }

    @Test
    @DisplayName("confirmResolution: validates blank fields and string length bounds")
    void testConfirmResolutionValidationBounds() {
        assertThrows(InvalidResolutionInputException.class, () ->
                resolutionService.confirmResolution(GRAPH, "inc-1",
                        new ResolutionInput("", "Title", "CONFIG_CHANGE", "Action", "sre"))
        );

        assertThrows(InvalidResolutionInputException.class, () ->
                resolutionService.confirmResolution(GRAPH, "inc-1",
                        new ResolutionInput("service-1", "", "CONFIG_CHANGE", "Action", "sre"))
        );

        assertThrows(InvalidResolutionInputException.class, () ->
                resolutionService.confirmResolution(GRAPH, "inc-1",
                        new ResolutionInput("service-1", "a".repeat(201), "CONFIG_CHANGE", "Action", "sre"))
        );

        assertThrows(InvalidResolutionInputException.class, () ->
                resolutionService.confirmResolution(GRAPH, "inc-1",
                        new ResolutionInput("service-1", "Title", "CONFIG_CHANGE", "", "sre"))
        );

        assertThrows(InvalidResolutionInputException.class, () ->
                resolutionService.confirmResolution(GRAPH, "inc-1",
                        new ResolutionInput("service-1", "Title", "CONFIG_CHANGE", "Action", ""))
        );
    }
}
