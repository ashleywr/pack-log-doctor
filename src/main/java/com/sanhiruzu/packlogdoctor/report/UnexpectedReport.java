package com.sanhiruzu.packlogdoctor.report;

import com.sanhiruzu.packlogdoctor.analysis.IssueSnapshot;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

record UnexpectedReport(
        List<IssueSnapshot> issues,
        Map<String, Integer> knownIssueCounts,
        Map<String, Integer> knownEventCounts,
        Map<String, String> knownNotes
) {
    UnexpectedReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
        knownIssueCounts = knownIssueCounts == null ? Map.of() : Map.copyOf(knownIssueCounts);
        knownEventCounts = knownEventCounts == null ? Map.of() : Map.copyOf(knownEventCounts);
        knownNotes = knownNotes == null ? Map.of() : Map.copyOf(knownNotes);
    }

    Map<String, Object> asJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("issues", issues);
        json.put("knownIssueCounts", knownIssueCounts);
        json.put("knownEventCounts", knownEventCounts);
        json.put("knownNotes", knownNotes);
        return json;
    }
}
