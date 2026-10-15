package com.devtrace.model;

import java.util.List;

public record ErrorSignatureNode(
        String id,
        String pattern,
        String errorType,
        List<String> keywords,
        List<String> associatedServiceIds
) {}
