package com.devtrace.engine;

import com.devtrace.model.InvestigationReport;
import com.devtrace.model.InvestigationRequest;

public interface InvestigationEngine {

    /**
     * Executes a full graph-assisted incident investigation.
     *
     * @param graphName Target FalkorDB graph.
     * @param request   Investigation request containing description, optional hints, and reference startedAt.
     * @return Complete structured InvestigationReport.
     */
    InvestigationReport investigate(String graphName, InvestigationRequest request);
}
