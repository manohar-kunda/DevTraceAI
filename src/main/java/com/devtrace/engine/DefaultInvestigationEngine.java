package com.devtrace.engine;

import com.devtrace.model.*;
import com.devtrace.repository.GraphRepository;
import com.devtrace.resolver.EntryPointResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Deterministic, rule-based graph investigation engine for DevTrace AI.
 *
 * Implements:
 * 1. Reference time isolation (uses request.startedAt; clock is fallback only and logged as assumption).
 * 2. Deterministic entry-point resolution with zero hallucination.
 * 3. Topological candidate neighborhood generation bounded by configurable downstream depth.
 * 4. Temporal deployment filtering bounded by configurable time window (deployments after startedAt ignored).
 * 5. Structured historical incident matching via shared ErrorSignatures and affected services.
 * 6. Rule-based tiering (STRONG, MODERATE, WEAK) with fully deterministic tie-breaks.
 * 7. Insufficient evidence detection when no candidate reaches MODERATE.
 * 8. Blast radius computation via upstream callers of top suspect.
 */
@Service
public class DefaultInvestigationEngine implements InvestigationEngine {

    private static final Logger log = LoggerFactory.getLogger(DefaultInvestigationEngine.class);

    public static final int DEFAULT_MAX_DOWNSTREAM_DEPTH = 3;
    public static final long DEFAULT_DEPLOYMENT_WINDOW_MS = 24L * 60 * 60 * 1000; // 24 hours

    private final GraphRepository graphRepository;
    private final EntryPointResolver entryPointResolver;
    private final Clock clock;
    private final int maxDownstreamDepth;
    private final long deploymentWindowMs;

    @org.springframework.beans.factory.annotation.Autowired
    public DefaultInvestigationEngine(
            GraphRepository graphRepository,
            EntryPointResolver entryPointResolver,
            @org.springframework.beans.factory.annotation.Autowired(required = false) Clock clock,
            @Value("${devtrace.investigation.max-depth:3}") int maxDownstreamDepth,
            @Value("${devtrace.investigation.window-ms:86400000}") long deploymentWindowMs
    ) {
        this.graphRepository = Objects.requireNonNull(graphRepository, "graphRepository cannot be null");
        this.entryPointResolver = Objects.requireNonNull(entryPointResolver, "entryPointResolver cannot be null");
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.maxDownstreamDepth = Math.max(1, maxDownstreamDepth);
        this.deploymentWindowMs = Math.max(1000L, deploymentWindowMs);
    }

    public DefaultInvestigationEngine(GraphRepository graphRepository, EntryPointResolver entryPointResolver) {
        this(graphRepository, entryPointResolver, Clock.systemUTC(), DEFAULT_MAX_DOWNSTREAM_DEPTH, DEFAULT_DEPLOYMENT_WINDOW_MS);
    }

    public DefaultInvestigationEngine(GraphRepository graphRepository, EntryPointResolver entryPointResolver, Clock clock) {
        this(graphRepository, entryPointResolver, clock, DEFAULT_MAX_DOWNSTREAM_DEPTH, DEFAULT_DEPLOYMENT_WINDOW_MS);
    }

    @Override
    public InvestigationReport investigate(String graphName, InvestigationRequest request) {
        Objects.requireNonNull(graphName, "graphName cannot be null");
        Objects.requireNonNull(request, "request cannot be null");

        List<String> assumptions = new ArrayList<>();
        List<String> limitations = new ArrayList<>(List.of(
                "Deterministic graph heuristic based on topology and recency; uncalibrated rule-based tiering without confidence probabilities.",
                "Analysis bounded by downstream depth " + maxDownstreamDepth + " and deployment window " + (deploymentWindowMs / 3600000) + "h.",
                "Requires human SRE verification before executing destructive remediations."
        ));

        // 1. Establish Reference Time (never reads System.currentTimeMillis directly)
        long referenceTime;
        if (request.startedAt() != null && request.startedAt() > 0) {
            referenceTime = request.startedAt();
        } else {
            referenceTime = clock.millis();
            assumptions.add("Reference incident time startedAt was not provided in request; assumed clock time " + referenceTime);
        }

        long windowStart = referenceTime - deploymentWindowMs;
        long windowEnd = referenceTime;

        // 2. Resolve Catalog & Entry Points
        List<ServiceNode> allServices = graphRepository.loadServices(graphName);
        List<ErrorSignatureNode> allSignatures = graphRepository.loadErrorSignatures(graphName);

        if (allServices.isEmpty()) {
            return buildInsufficientEvidenceReport(
                    request,
                    new EntryPointsResult(Optional.empty(), Optional.empty()),
                    List.of("Graph contains zero Service nodes (graph is empty)."),
                    assumptions, limitations
            );
        }

        EntryPointsResult entryPoints = entryPointResolver.resolve(
                request,
                allServices,
                allSignatures,
                Set.of()
        );

        // Check if graph has any services or if query is unresolvable
        if (!entryPoints.hasConfidentMatches()) {
            return buildInsufficientEvidenceReport(
                    request, entryPoints,
                    List.of("No confident symptom service or error signature could be resolved from the request."),
                    assumptions, limitations
            );
        }

        // 3. Candidate Generation (Symptom service + downstream dependencies up to maxDownstreamDepth)
        Optional<ResolvedService> symptomServiceOpt = entryPoints.resolvedService();
        if (symptomServiceOpt.isEmpty()) {
            return buildInsufficientEvidenceReport(
                    request, entryPoints,
                    List.of("No symptom service could be identified in the graph topology."),
                    assumptions, limitations
            );
        }

        ResolvedService symptomService = symptomServiceOpt.get();
        DependenciesResult downstreamDeps = graphRepository.findDependencies(
                graphName, symptomService.serviceId(), maxDownstreamDepth, 50
        );

        // Map candidateId -> hop distance from symptom service
        Map<String, Integer> candidateHops = new LinkedHashMap<>();
        candidateHops.put(symptomService.serviceId(), 0);
        for (ServiceHop hop : downstreamDeps.allDependencies()) {
            candidateHops.putIfAbsent(hop.serviceId(), hop.hops());
        }

        Set<String> candidateServiceIds = Collections.unmodifiableSet(candidateHops.keySet());

        // Re-resolve entry points with candidate neighborhood for signature tie-breaking if needed
        if (entryPoints.resolvedSignature().isEmpty() && !candidateServiceIds.isEmpty()) {
            entryPoints = entryPointResolver.resolve(request, allServices, allSignatures, candidateServiceIds);
        }

        // Map candidateId -> ServiceNode metadata
        Map<String, ServiceNode> serviceMap = allServices.stream()
                .collect(Collectors.toMap(ServiceNode::id, s -> s));

        // 4. Retrieve Deployments in Window across entire graph
        List<DeploymentRecord> allDeployments = graphRepository.findAllDeploymentsInWindow(graphName, windowStart, windowEnd);

        List<DeploymentRecord> candidateDeployments = new ArrayList<>();
        List<IgnoredChange> ignoredChanges = new ArrayList<>();

        for (DeploymentRecord dep : allDeployments) {
            if (candidateServiceIds.contains(dep.serviceId())) {
                candidateDeployments.add(dep);
            } else {
                ServiceNode svc = serviceMap.get(dep.serviceId());
                String svcName = svc != null ? svc.name() : dep.serviceId();
                ignoredChanges.add(new IgnoredChange(
                        dep.deploymentId(),
                        dep.serviceId(),
                        svcName,
                        "DEPLOYMENT",
                        dep.deployedAt(),
                        "recent changes outside the dependency neighborhood, not considered"
                ));
            }
        }

        // 5. Historical Incident Retrieval (Structured matching only)
        List<SimilarIncidentRecord> similarIncidents = List.of();
        Optional<ResolvedErrorSignature> sigOpt = entryPoints.resolvedSignature();
        if (sigOpt.isPresent()) {
            similarIncidents = graphRepository.findSimilarIncidents(
                    graphName, sigOpt.get().signatureId(), candidateServiceIds, request.incidentId()
            );
        }

        // Track missing evidence items
        List<String> missingEvidence = new ArrayList<>();
        if (sigOpt.isEmpty()) {
            missingEvidence.add("no matching signature resolved from request");
        }
        if (candidateDeployments.isEmpty()) {
            missingEvidence.add("no deployments in the " + (deploymentWindowMs / 3600000) + "h window on candidate services");
        }
        if (similarIncidents.isEmpty()) {
            missingEvidence.add("no similar historical incident matching signature on candidate services");
        }

        // 6. Build Candidate Hypotheses & Rule-Based Tiering
        List<CandidateHypothesis> hypotheses = new ArrayList<>();

        for (String cId : candidateServiceIds) {
            ServiceNode node = serviceMap.get(cId);
            String serviceName = node != null ? node.name() : cId;
            int hopDistance = candidateHops.getOrDefault(cId, 0);

            // Find deployments on this candidate
            List<DeploymentRecord> serviceDeps = candidateDeployments.stream()
                    .filter(d -> d.serviceId().equals(cId))
                    .sorted(Comparator.comparingLong(DeploymentRecord::deployedAt).reversed())
                    .toList();

            // Find matching historical incidents affecting this candidate
            List<SimilarIncidentRecord> serviceIncidents = similarIncidents.stream()
                    .filter(inc -> inc.affectedServiceIds().contains(cId))
                    .toList();

            List<EvidenceItem> evidenceItems = new ArrayList<>();
            Long mostRecentChange = null;

            if (!serviceDeps.isEmpty()) {
                DeploymentRecord latestDep = serviceDeps.get(0);
                mostRecentChange = latestDep.deployedAt();
                long minutesBefore = (referenceTime - latestDep.deployedAt()) / (60 * 1000);
                String isoTime = java.time.Instant.ofEpochMilli(latestDep.deployedAt()).toString();
                String changeSummaryText = (latestDep.changeSummary() != null && !latestDep.changeSummary().isBlank())
                        ? latestDep.changeSummary()
                        : "No change summary recorded.";
                evidenceItems.add(new EvidenceItem(
                        "findRecentDeployments",
                        latestDep.deploymentId(),
                        "DEPLOYMENT",
                        "Recent deployment " + latestDep.version() + " deployed at " + isoTime + " (" + latestDep.deployedAt() + ", " + minutesBefore + "m before incident). Change summary: " + changeSummaryText
                ));
            }

            ResolutionRecord confirmedRes = null;
            if (!serviceIncidents.isEmpty()) {
                for (SimilarIncidentRecord inc : serviceIncidents) {
                    evidenceItems.add(new EvidenceItem(
                            "findSimilarIncidents",
                            inc.incidentId(),
                            "HISTORICAL_INCIDENT",
                            "The same service was affected by the same error signature in " + inc.incidentId() + " (" + inc.title() + ")"
                    ));
                    if (inc.hasConfirmedResolution()) {
                        ResolutionRecord res = inc.confirmedResolution();
                        String isoDate = java.time.Instant.ofEpochMilli(res.confirmedAt()).toString();
                        evidenceItems.add(new EvidenceItem(
                                "findSimilarIncidents",
                                res.id(),
                                "CONFIRMED_RESOLUTION",
                                "Consider the previously confirmed remediation " + res.id() + " (source: " + res.source() + ", confirmed: " + isoDate + ", from incident " + inc.incidentId() + "): " + res.title()
                        ));
                    }
                }
                confirmedRes = serviceIncidents.get(0).confirmedResolution();
            }

            // hopDistance is a direct field on CandidateHypothesis, not an EvidenceItem.

            boolean hasDeployment = !serviceDeps.isEmpty();
            boolean hasSignatureEvidence = !serviceIncidents.isEmpty();

            RankingTier tier;
            String reason;

            if (hasDeployment && hasSignatureEvidence) {
                tier = RankingTier.STRONG;
                reason = "Recent deployment (" + serviceDeps.get(0).version() + ") in window and the same service was affected by the same error signature in " +
                        serviceIncidents.get(0).incidentId() + ".";
            } else if (hasDeployment) {
                tier = RankingTier.MODERATE;
                reason = "Recent deployment (" + serviceDeps.get(0).version() + ") in window, but no historical incidents match the error signature on this service.";
            } else if (hasSignatureEvidence) {
                tier = RankingTier.MODERATE;
                reason = "The same service was affected by matching error signature in " + serviceIncidents.get(0).incidentId() + ", but no deployments recorded within the 24h window.";
            } else {
                tier = RankingTier.WEAK;
                reason = "Reachable in downstream dependency chain at " + hopDistance + " hop(s), but has no recent deployments and no historical signature matches.";
            }

            hypotheses.add(new CandidateHypothesis(
                    cId,
                    serviceName,
                    tier,
                    0, // Assigned after sorting
                    hopDistance,
                    mostRecentChange,
                    Collections.unmodifiableList(evidenceItems),
                    reason,
                    confirmedRes != null,
                    confirmedRes
            ));
        }

        // 7. Deterministic Sorting:
        // Tier (STRONG > MODERATE > WEAK)
        // -> Most recent change timestamp DESC (nulls last)
        // -> Hop distance ASC
        // -> Service ID ASC
        hypotheses.sort((a, b) -> {
            int tierCmp = a.tier().compareTo(b.tier()); // STRONG=0, MODERATE=1, WEAK=2
            if (tierCmp != 0) return tierCmp;

            // Recency: more recent first (larger timestamp)
            if (a.mostRecentChangeTimestamp() != null && b.mostRecentChangeTimestamp() != null) {
                int tsCmp = Long.compare(b.mostRecentChangeTimestamp(), a.mostRecentChangeTimestamp());
                if (tsCmp != 0) return tsCmp;
            } else if (a.mostRecentChangeTimestamp() != null) {
                return -1;
            } else if (b.mostRecentChangeTimestamp() != null) {
                return 1;
            }

            // Hop distance: smaller first
            int hopCmp = Integer.compare(a.hopDistance(), b.hopDistance());
            if (hopCmp != 0) return hopCmp;

            // Alphanumeric tie-break
            return a.serviceId().compareTo(b.serviceId());
        });

        // 8. Partition into STRONG/MODERATE (hypotheses) and WEAK (otherServicesInNeighborhood)
        List<CandidateHypothesis> strongAndModerate = new ArrayList<>();
        List<OtherServiceNode> otherServices = new ArrayList<>();

        int currentRank = 1;
        for (CandidateHypothesis h : hypotheses) {
            if (h.tier() == RankingTier.STRONG || h.tier() == RankingTier.MODERATE) {
                strongAndModerate.add(new CandidateHypothesis(
                        h.serviceId(),
                        h.serviceName(),
                        h.tier(),
                        currentRank++,
                        h.hopDistance(),
                        h.mostRecentChangeTimestamp(),
                        h.evidence(),
                        h.plainLanguageReason(),
                        h.hasConfirmedResolution(),
                        h.confirmedResolution()
                ));
            } else {
                otherServices.add(new OtherServiceNode(h.serviceId(), h.serviceName(), h.hopDistance()));
            }
        }

        // 9. Determine Status and Next Steps
        boolean hasViableCandidate = !strongAndModerate.isEmpty();
        String status = hasViableCandidate ? "HYPOTHESES_RANKED" : "INSUFFICIENT_EVIDENCE";

        CandidateHypothesis topCandidate = strongAndModerate.isEmpty() ? null : strongAndModerate.get(0);

        // 10. Graph Relationships: Paths from symptom to candidates & blast radius
        Map<String, DependencyPath> paths = new LinkedHashMap<>();
        for (CandidateHypothesis h : strongAndModerate) {
            if (h.hopDistance() > 0) {
                graphRepository.findDependencyPath(graphName, symptomService.serviceId(), h.serviceId(), maxDownstreamDepth + 1)
                        .ifPresent(p -> paths.put(h.serviceId(), p));
            }
        }

        String blastTarget = (topCandidate != null && hasViableCandidate) ? topCandidate.serviceId() : symptomService.serviceId();
        DependentsResult blastRadius = graphRepository.findDependents(graphName, blastTarget, 4, 20);

        GraphRelationships graphRelationships = new GraphRelationships(
                Collections.unmodifiableMap(paths),
                blastRadius
        );

        // 11. Suggested Next Steps
        List<SuggestedNextStep> nextSteps = new ArrayList<>();
        if (hasViableCandidate && topCandidate != null) {
            List<SimilarIncidentRecord> candidateIncidents = similarIncidents.stream()
                    .filter(inc -> inc.affectedServiceIds().contains(topCandidate.serviceId()))
                    .toList();
            for (SimilarIncidentRecord inc : candidateIncidents) {
                if (inc.hasConfirmedResolution()) {
                    nextSteps.add(SuggestedNextStep.fromConfirmedResolution(inc.confirmedResolution()));
                }
            }
            if (topCandidate.mostRecentChangeTimestamp() != null) {
                nextSteps.add(SuggestedNextStep.genericDiagnostic(
                        "Rollback Candidate Deployment",
                        "Evaluate immediate rollback or canary draining of recent change on " + topCandidate.serviceName() + "."
                ));
            }
            nextSteps.add(SuggestedNextStep.genericDiagnostic(
                    "Inspect Connection & Thread Telemetry",
                    "Query metrics and thread dumps for " + topCandidate.serviceName() + " to verify resource saturation."
            ));
        } else {
            nextSteps.add(SuggestedNextStep.genericDiagnostic(
                    "Check Application Logs",
                    "Inspect raw logs on " + symptomService.serviceName() + " to identify stack traces or unmapped error signatures."
            ));
            nextSteps.add(SuggestedNextStep.genericDiagnostic(
                    "Verify Network & Upstream Health",
                    "Verify infrastructure health and check if external network dependencies experienced packet loss."
            ));
        }

        ObservedFacts observedFacts = new ObservedFacts(
                Collections.unmodifiableList(candidateDeployments),
                Collections.unmodifiableList(similarIncidents),
                Collections.unmodifiableList(missingEvidence)
        );

        return new InvestigationReport(
                status,
                request,
                entryPoints,
                observedFacts,
                graphRelationships,
                Collections.unmodifiableList(strongAndModerate),
                Collections.unmodifiableList(otherServices),
                Collections.unmodifiableList(ignoredChanges),
                Collections.unmodifiableList(nextSteps),
                Collections.unmodifiableList(limitations),
                Collections.unmodifiableList(assumptions)
        );
    }

    private InvestigationReport buildInsufficientEvidenceReport(
            InvestigationRequest request,
            EntryPointsResult entryPoints,
            List<String> missingEvidence,
            List<String> assumptions,
            List<String> limitations
    ) {
        ObservedFacts observedFacts = new ObservedFacts(List.of(), List.of(), missingEvidence);
        GraphRelationships emptyRelationships = new GraphRelationships(Map.of(), new DependentsResult("", List.of(), List.of()));

        List<SuggestedNextStep> nextSteps = List.of(
                SuggestedNextStep.genericDiagnostic("Check Application Logs", "Inspect raw service logs to find stack traces or error keywords."),
                SuggestedNextStep.genericDiagnostic("Clarify Symptom Service", "Provide an explicit service name or hint in the investigation request.")
        );

        return new InvestigationReport(
                "INSUFFICIENT_EVIDENCE",
                request,
                entryPoints,
                observedFacts,
                emptyRelationships,
                List.of(),
                List.of(),
                List.of(),
                nextSteps,
                limitations,
                assumptions
        );
    }
}
