package com.devtrace.resolver;

import com.devtrace.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DefaultEntryPointResolverTest {

    private DefaultEntryPointResolver resolver;
    private List<ServiceNode> sampleServices;
    private List<ErrorSignatureNode> sampleSignatures;

    @BeforeEach
    void setUp() {
        resolver = new DefaultEntryPointResolver();

        sampleServices = List.of(
                new ServiceNode("service-gateway", "api-gateway", "Edge", "MICROSERVICE", 1,
                        List.of("api-gateway", "gateway", "edge-router", "ingress")),
                new ServiceNode("service-auth", "auth-service", "Security", "MICROSERVICE", 1,
                        List.of("auth-service", "auth", "authentication", "jwt", "oauth", "identity")),
                new ServiceNode("service-checkout", "checkout-service", "Checkout", "MICROSERVICE", 1,
                        List.of("checkout-service", "checkout", "cart", "web-checkout")),
                new ServiceNode("service-order", "order-service", "Ordering", "MICROSERVICE", 1,
                        List.of("order-service", "orders", "order-processing", "fulfillment")),
                new ServiceNode("service-payment", "payment-service", "Payments", "MICROSERVICE", 1,
                        List.of("payment-service", "payment", "payments", "checkout-payment", "payment-gateway"))
        );

        sampleSignatures = List.of(
                new ErrorSignatureNode(
                        "err-sig-payment-pool-timeout",
                        "JedisConnectionException: Could not get a resource from the pool",
                        "TIMEOUT",
                        List.of("payment timeout", "payment timeouts", "connection pool timeout", "pool timeout", "timeout"),
                        List.of("service-payment")
                ),
                new ErrorSignatureNode(
                        "err-sig-jwt-expired",
                        "SignatureVerificationException / ClockSkew",
                        "AUTHENTICATION",
                        List.of("jwt", "expired token", "token verification", "clock skew", "auth timeout", "timeout"),
                        List.of("service-auth")
                ),
                new ErrorSignatureNode(
                        "err-sig-smtp-disconnect",
                        "SMTPConnectionClosedException",
                        "NETWORK",
                        List.of("smtp", "disconnect", "mail delivery"),
                        List.of("service-notification")
                )
        );
    }

    @Test
    @DisplayName("Normalization handles case, punctuation, and plural forms deterministically")
    void testNormalization() {
        String input = "Payment-Timeouts occurred during Checkout requests!!!";
        String normalized = DefaultEntryPointResolver.normalize(input);

        // "Payment-Timeouts" -> "payment timeout", "occurred" -> "occurred", "Checkout" -> "checkout", "requests" -> "request"
        assertEquals("payment timeout occurred during checkout request", normalized);
    }

    @Test
    @DisplayName("Longest phrase match strictly beats single-word matches")
    void testLongestPhraseBeatsSingleWord() {
        // "payment timeouts" matches both multi-word "payment timeout" (2 words) and generic "timeout" (1 word)
        InvestigationRequest request = new InvestigationRequest(
                "Orders are failing due to payment timeouts in the gateway"
        );

        EntryPointsResult result = resolver.resolve(request, sampleServices, sampleSignatures, Set.of("service-payment"));

        assertTrue(result.errorSignature().isPresent());
        ResolvedErrorSignature sig = result.errorSignature().get();

        assertEquals("err-sig-payment-pool-timeout", sig.signatureId());
        assertTrue(sig.evidence().matchedToken().startsWith("payment timeout"));
        assertTrue(sig.evidence().explanation().contains("payment timeout"));
    }

    @Test
    @DisplayName("Structured hints strictly override free-text extraction")
    void testStructuredHintOverride() {
        // Text mentions checkout, but structured hint explicitly specifies service-payment and jwt error
        InvestigationRequest request = new InvestigationRequest(
                "Checkout requests are failing after the latest deployment",
                "service-payment",
                "err-sig-jwt-expired"
        );

        EntryPointsResult result = resolver.resolve(request, sampleServices, sampleSignatures, Set.of());

        assertTrue(result.service().isPresent());
        assertEquals("service-payment", result.service().get().serviceId());
        assertEquals("STRUCTURED_HINT", result.service().get().evidence().matchSource());

        assertTrue(result.errorSignature().isPresent());
        assertEquals("err-sig-jwt-expired", result.errorSignature().get().signatureId());
        assertEquals("STRUCTURED_HINT", result.errorSignature().get().evidence().matchSource());
    }

    @Test
    @DisplayName("Auth/JWT distractor does NOT match when checkout payment timeouts are reported")
    void testAuthDistractorRejection() {
        // Input contains "checkout" and "payment timeouts"
        InvestigationRequest request = new InvestigationRequest(
                "Checkout requests are failing after deployment and payment timeouts have surged"
        );

        // Neighborhood is checkout's call chain: checkout -> order -> payment
        Set<String> neighborhood = Set.of("service-checkout", "service-order", "service-payment");

        EntryPointsResult result = resolver.resolve(request, sampleServices, sampleSignatures, neighborhood);

        assertTrue(result.service().isPresent());
        assertEquals("service-checkout", result.service().get().serviceId());

        assertTrue(result.errorSignature().isPresent());
        assertEquals("err-sig-payment-pool-timeout", result.errorSignature().get().signatureId());
        assertNotEquals("err-sig-jwt-expired", result.errorSignature().get().signatureId(),
                "Auth/JWT error signature must be rejected");
    }

    @Test
    @DisplayName("Neighborhood tie-break selects candidate historically associated with reachable services")
    void testNeighborhoodTieBreak() {
        // Single word "timeout" matches BOTH payment-pool-timeout and jwt-expired with identical word count
        InvestigationRequest request = new InvestigationRequest(
                "Unspecified timeout observed"
        );

        // Reachable neighborhood includes service-payment, but not service-auth
        Set<String> neighborhood = Set.of("service-checkout", "service-order", "service-payment");

        EntryPointsResult result = resolver.resolve(request, sampleServices, sampleSignatures, neighborhood);

        assertTrue(result.errorSignature().isPresent());
        assertEquals("err-sig-payment-pool-timeout", result.errorSignature().get().signatureId(),
                "Candidate historically associated with reachable payment service must win tie-break over disjoint auth service");
    }

    @Test
    @DisplayName("Empty or unresolvable input returns explicit empty Optional, never a guess")
    void testEmptyOrNoMatchInput() {
        InvestigationRequest unresolvable = new InvestigationRequest(
                "The green submit button is frozen on the screen"
        );

        EntryPointsResult result = resolver.resolve(unresolvable, sampleServices, sampleSignatures, Set.of());

        assertTrue(result.service().isEmpty(), "Must return empty Optional for unrecognized services, never guess");
        assertTrue(result.errorSignature().isEmpty(), "Must return empty Optional for unrecognized errors, never guess");
    }

    @Test
    @DisplayName("Null and blank descriptions are safely handled returning empty Optionals")
    void testNullAndBlankDescription() {
        EntryPointsResult nullResult = resolver.resolve(new InvestigationRequest(null), sampleServices, sampleSignatures, Set.of());
        assertTrue(nullResult.service().isEmpty());
        assertTrue(nullResult.errorSignature().isEmpty());

        EntryPointsResult blankResult = resolver.resolve(new InvestigationRequest("   "), sampleServices, sampleSignatures, Set.of());
        assertTrue(blankResult.service().isEmpty());
        assertTrue(blankResult.errorSignature().isEmpty());

        EntryPointsResult nullRequestResult = resolver.resolve(null, sampleServices, sampleSignatures, Set.of());
        assertTrue(nullRequestResult.service().isEmpty());
        assertTrue(nullRequestResult.errorSignature().isEmpty());
    }
}
