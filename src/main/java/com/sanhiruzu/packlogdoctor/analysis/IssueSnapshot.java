package com.sanhiruzu.packlogdoctor.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record IssueSnapshot(
        String signature,
        IssueCategory category,
        Severity severity,
        int count,
        Instant firstSeen,
        Instant lastSeen,
        Map<String, Integer> loggerCounts,
        Map<String, Integer> likelyModCounts,
        List<String> examples,
        List<String> evidence,
        List<FixSuggestion> suggestions
) {
    public IssueSnapshot(
            String signature,
            IssueCategory category,
            Severity severity,
            int count,
            Instant firstSeen,
            Instant lastSeen,
            Map<String, Integer> loggerCounts,
            Map<String, Integer> likelyModCounts,
            List<String> examples,
            List<FixSuggestion> suggestions
    ) {
        this(signature, category, severity, count, firstSeen, lastSeen, loggerCounts, likelyModCounts, examples, List.of(), suggestions);
    }
}
