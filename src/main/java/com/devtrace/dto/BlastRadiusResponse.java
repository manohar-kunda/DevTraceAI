package com.devtrace.dto;

import com.devtrace.model.ServiceHop;
import java.util.List;

public record BlastRadiusResponse(
        String targetServiceId,
        List<ServiceHop> directDependents,
        List<ServiceHop> indirectDependents,
        int totalDependents
) {}
