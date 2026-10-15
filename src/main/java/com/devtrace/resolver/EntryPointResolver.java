package com.devtrace.resolver;

import com.devtrace.model.EntryPointsResult;
import com.devtrace.model.ErrorSignatureNode;
import com.devtrace.model.InvestigationRequest;
import com.devtrace.model.ServiceNode;

import java.util.List;
import java.util.Set;

public interface EntryPointResolver {

    /**
     * Resolves entry-point service and error signature from an investigation request
     * and pre-loaded graph catalog data.
     *
     * Pure Java implementation with zero direct database queries.
     *
     * @param request               Incoming investigation alert or user request.
     * @param availableServices     Catalog of services with aliases.
     * @param availableSignatures   Catalog of error signatures with keywords.
     * @param candidateNeighborhood Optional set of service IDs reachable from candidate symptom.
     * @return EntryPointsResult containing resolved nodes with evidence, or empty optionals.
     */
    EntryPointsResult resolve(
            InvestigationRequest request,
            List<ServiceNode> availableServices,
            List<ErrorSignatureNode> availableSignatures,
            Set<String> candidateNeighborhood
    );
}
