package com.devtrace.config;

import com.devtrace.exception.UnauthorizedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Shared token authentication interceptor for protected endpoints.
 *
 * Rules:
 * 1. Checks header 'X-DevTrace-Token'.
 * 2. Compares with DEVTRACE_API_TOKEN using constant-time MessageDigest.isEqual.
 * 3. Fail closed: if DEVTRACE_API_TOKEN is unset or blank, endpoint is disabled with 401.
 * 4. Never logs or echoes the secret token value.
 */
@Component
public class DevTraceTokenInterceptor implements HandlerInterceptor {

    public static final String TOKEN_HEADER = "X-DevTrace-Token";

    private final String configuredToken;

    public DevTraceTokenInterceptor(@Value("${DEVTRACE_API_TOKEN:${devtrace.api-token:}}") String configuredToken) {
        String token = (configuredToken != null && !configuredToken.isBlank())
                ? configuredToken.trim()
                : System.getProperty("DEVTRACE_API_TOKEN");
        if (token == null || token.isBlank()) {
            token = System.getenv("DEVTRACE_API_TOKEN");
        }
        this.configuredToken = token != null ? token.trim() : "";
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Only enforce token auth on POST /api/incidents/*/resolution
        if ("POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().endsWith("/resolution")) {
            // Fail closed: if environment variable is not configured, disable the endpoint
            if (configuredToken.isBlank()) {
                throw new UnauthorizedException(
                        "The resolution confirmation endpoint is disabled because DEVTRACE_API_TOKEN is not configured on the server."
                );
            }

            String providedToken = request.getHeader(TOKEN_HEADER);
            if (providedToken == null || providedToken.isBlank()) {
                throw new UnauthorizedException("Missing required '" + TOKEN_HEADER + "' authentication header.");
            }

            byte[] a = configuredToken.getBytes(StandardCharsets.UTF_8);
            byte[] b = providedToken.getBytes(StandardCharsets.UTF_8);

            // Constant-time comparison to prevent timing attacks
            if (!MessageDigest.isEqual(a, b)) {
                throw new UnauthorizedException("Invalid authentication token provided in '" + TOKEN_HEADER + "' header.");
            }
        }

        return true;
    }
}
