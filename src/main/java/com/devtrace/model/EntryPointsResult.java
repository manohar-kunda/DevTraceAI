package com.devtrace.model;

import java.util.Optional;

public record EntryPointsResult(
        Optional<ResolvedService> service,
        Optional<ResolvedErrorSignature> errorSignature
) {}
