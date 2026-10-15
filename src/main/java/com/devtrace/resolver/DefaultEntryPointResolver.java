package com.devtrace.resolver;

import com.devtrace.model.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DefaultEntryPointResolver implements EntryPointResolver {

    private static final Set<String> UNSTEMMED_TERMS = Set.of(
            "redis", "postgres", "status", "ingress", "access", "process", "gateway"
    );

    @Override
    public EntryPointsResult resolve(
            InvestigationRequest request,
            List<ServiceNode> availableServices,
            List<ErrorSignatureNode> availableSignatures,
            Set<String> candidateNeighborhood
    ) {
        if (request == null) {
            return new EntryPointsResult(Optional.empty(), Optional.empty());
        }

        Optional<ResolvedService> resolvedService = resolveService(request, availableServices);

        // If candidate neighborhood was not explicitly passed, but a symptom service was resolved,
        // candidate neighborhood can include the symptom service and its dependents if known,
        // or caller can pass it.
        Optional<ResolvedErrorSignature> resolvedSignature = resolveErrorSignature(
                request,
                availableSignatures,
                candidateNeighborhood != null ? candidateNeighborhood : Set.of()
        );

        return new EntryPointsResult(resolvedService, resolvedSignature);
    }

    private Optional<ResolvedService> resolveService(InvestigationRequest request, List<ServiceNode> services) {
        if (services == null || services.isEmpty()) {
            return Optional.empty();
        }

        // 1. Structured Hint Override
        String hint = request.serviceHint();
        if (hint != null && !hint.isBlank()) {
            String normHint = normalize(hint);
            for (ServiceNode s : services) {
                if (s.id().equalsIgnoreCase(hint.trim()) || normalize(s.name()).equals(normHint)) {
                    MatchedEvidence ev = new MatchedEvidence(
                            hint.trim(),
                            "STRUCTURED_HINT",
                            "Resolved service via structured alert hint: " + hint.trim()
                    );
                    return Optional.of(new ResolvedService(s.id(), s.name(), s.serviceType(), ev));
                }
                for (String alias : s.aliases()) {
                    if (normalize(alias).equals(normHint)) {
                        MatchedEvidence ev = new MatchedEvidence(
                                hint.trim(),
                                "STRUCTURED_HINT",
                                "Resolved service via structured alert hint matching alias '" + alias + "'"
                        );
                        return Optional.of(new ResolvedService(s.id(), s.name(), s.serviceType(), ev));
                    }
                }
            }
            // If explicit hint does not match any known service, return empty (no guessing)
            return Optional.empty();
        }

        // 2. Free-Text Description Extraction
        String desc = request.description();
        if (desc == null || desc.isBlank()) {
            return Optional.empty();
        }

        String normDesc = normalize(desc);
        if (normDesc.isBlank()) {
            return Optional.empty();
        }

        List<ServiceMatchCandidate> candidates = new ArrayList<>();

        for (ServiceNode s : services) {
            // Check service name and id
            checkServicePhrase(normDesc, s.name(), s, candidates);
            checkServicePhrase(normDesc, s.id(), s, candidates);

            // Check aliases
            for (String alias : s.aliases()) {
                checkServicePhrase(normDesc, alias, s, candidates);
            }
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Sort candidates:
        // 1. Longest phrase by word count descending
        // 2. Longest phrase by character length descending
        // 3. Earliest first occurrence in text (primary symptom subject)
        candidates.sort((a, b) -> {
            int wc = Integer.compare(b.wordCount, a.wordCount);
            if (wc != 0) return wc;
            int cl = Integer.compare(b.normPhrase.length(), a.normPhrase.length());
            if (cl != 0) return cl;
            return Integer.compare(a.firstIndex, b.firstIndex);
        });

        ServiceMatchCandidate top = candidates.get(0);
        MatchedEvidence ev = new MatchedEvidence(
                top.rawPhrase,
                "FREE_TEXT_KEYWORD",
                "Extracted symptom service from text via phrase '" + top.rawPhrase + "'"
        );

        return Optional.of(new ResolvedService(top.service.id(), top.service.name(), top.service.serviceType(), ev));
    }

    private void checkServicePhrase(String normDesc, String rawPhrase, ServiceNode s, List<ServiceMatchCandidate> candidates) {
        if (rawPhrase == null || rawPhrase.isBlank()) {
            return;
        }
        String normPhrase = normalize(rawPhrase);
        if (normPhrase.isBlank()) {
            return;
        }

        int idx = indexOfWholePhrase(normDesc, normPhrase);
        if (idx >= 0) {
            int wordCount = countWords(normPhrase);
            candidates.add(new ServiceMatchCandidate(s, rawPhrase, normPhrase, wordCount, idx));
        }
    }

    private Optional<ResolvedErrorSignature> resolveErrorSignature(
            InvestigationRequest request,
            List<ErrorSignatureNode> signatures,
            Set<String> neighborhood
    ) {
        if (signatures == null || signatures.isEmpty()) {
            return Optional.empty();
        }

        // 1. Structured Hint Override
        String hint = request.errorHint();
        if (hint != null && !hint.isBlank()) {
            String trimmedHint = hint.trim();
            for (ErrorSignatureNode e : signatures) {
                if (e.id().equalsIgnoreCase(trimmedHint) || e.errorType().equalsIgnoreCase(trimmedHint)) {
                    MatchedEvidence ev = new MatchedEvidence(
                            trimmedHint,
                            "STRUCTURED_HINT",
                            "Resolved error signature via structured alert hint: " + trimmedHint
                    );
                    return Optional.of(new ResolvedErrorSignature(e.id(), e.pattern(), e.errorType(), ev));
                }
            }
            return Optional.empty();
        }

        // 2. Free-Text Description Extraction
        String desc = request.description();
        if (desc == null || desc.isBlank()) {
            return Optional.empty();
        }

        String normDesc = normalize(desc);
        if (normDesc.isBlank()) {
            return Optional.empty();
        }

        List<SignatureMatchCandidate> candidates = new ArrayList<>();

        for (ErrorSignatureNode e : signatures) {
            for (String kw : e.keywords()) {
                if (kw == null || kw.isBlank()) continue;
                String normKw = normalize(kw);
                if (normKw.isBlank()) continue;

                int idx = indexOfWholePhrase(normDesc, normKw);
                if (idx >= 0) {
                    int wordCount = countWords(normKw);
                    candidates.add(new SignatureMatchCandidate(e, kw, normKw, wordCount, idx));
                }
            }
        }

        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Group by signature, keeping best match per signature
        Map<String, SignatureMatchCandidate> bestBySig = new LinkedHashMap<>();
        for (SignatureMatchCandidate c : candidates) {
            String sigId = c.signature.id();
            if (!bestBySig.containsKey(sigId)) {
                bestBySig.put(sigId, c);
            } else {
                SignatureMatchCandidate existing = bestBySig.get(sigId);
                if (c.wordCount > existing.wordCount || (c.wordCount == existing.wordCount && c.normPhrase.length() > existing.normPhrase.length())) {
                    bestBySig.put(sigId, c);
                }
            }
        }

        List<SignatureMatchCandidate> distinctCandidates = new ArrayList<>(bestBySig.values());

        // Sort candidates:
        // 1. Word count descending (longest multi-word phrase first)
        // 2. Character length descending
        distinctCandidates.sort((a, b) -> {
            int wc = Integer.compare(b.wordCount, a.wordCount);
            if (wc != 0) return wc;
            return Integer.compare(b.normPhrase.length(), a.normPhrase.length());
        });

        SignatureMatchCandidate top = distinctCandidates.get(0);

        // Check for tie-break: if second candidate has identical wordCount and length
        if (distinctCandidates.size() > 1) {
            SignatureMatchCandidate second = distinctCandidates.get(1);
            if (second.wordCount == top.wordCount && second.normPhrase.length() == top.normPhrase.length()) {
                // Rule 2: Neighborhood scoping
                boolean topInNeigh = intersects(top.signature.associatedServiceIds(), neighborhood);
                boolean secondInNeigh = intersects(second.signature.associatedServiceIds(), neighborhood);

                if (!topInNeigh && secondInNeigh) {
                    top = second;
                }
            }
        }

        MatchedEvidence ev = new MatchedEvidence(
                top.rawPhrase,
                "FREE_TEXT_KEYWORD",
                "Extracted error signature from text via keyword phrase '" + top.rawPhrase + "'"
        );

        return Optional.of(new ResolvedErrorSignature(top.signature.id(), top.signature.pattern(), top.signature.errorType(), ev));
    }

    private boolean intersects(List<String> list, Set<String> set) {
        if (list == null || list.isEmpty() || set == null || set.isEmpty()) {
            return false;
        }
        for (String item : list) {
            if (set.contains(item)) {
                return true;
            }
        }
        return false;
    }

    public static String normalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        // 1. Lowercase
        String lower = text.toLowerCase(Locale.ROOT);

        // 2. Punctuation stripping: replace any non-alphanumeric character with space
        String clean = lower.replaceAll("[^a-z0-9\\s]", " ");

        // 3. Tokenize & stem plurals
        String[] tokens = clean.split("\\s+");
        List<String> normalizedTokens = new ArrayList<>(tokens.length);

        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty()) continue;

            normalizedTokens.add(normalizeToken(token));
        }

        return String.join(" ", normalizedTokens);
    }

    private static String normalizeToken(String token) {
        if (UNSTEMMED_TERMS.contains(token)) {
            return token;
        }
        if (token.endsWith("ies") && token.length() > 4) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.endsWith("ses") && token.length() > 4) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("ss")) {
            return token;
        }
        if (token.endsWith("s") && token.length() > 3) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private int indexOfWholePhrase(String normText, String normPhrase) {
        String paddedText = " " + normText + " ";
        String paddedPhrase = " " + normPhrase + " ";
        int idx = paddedText.indexOf(paddedPhrase);
        if (idx < 0) {
            return -1;
        }
        return idx; // Padded index reflects order of occurrence
    }

    private int countWords(String phrase) {
        if (phrase == null || phrase.isBlank()) return 0;
        return phrase.trim().split("\\s+").length;
    }

    private record ServiceMatchCandidate(
            ServiceNode service,
            String rawPhrase,
            String normPhrase,
            int wordCount,
            int firstIndex
    ) {}

    private record SignatureMatchCandidate(
            ErrorSignatureNode signature,
            String rawPhrase,
            String normPhrase,
            int wordCount,
            int firstIndex
    ) {}
}
