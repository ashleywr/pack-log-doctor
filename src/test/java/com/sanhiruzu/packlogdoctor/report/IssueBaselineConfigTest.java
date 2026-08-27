package com.sanhiruzu.packlogdoctor.report;

import com.sanhiruzu.packlogdoctor.analysis.IssueCategory;
import com.sanhiruzu.packlogdoctor.analysis.IssueSnapshot;
import com.sanhiruzu.packlogdoctor.analysis.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IssueBaselineConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void matchesReviewedIssuesByLoggerAndMessageSubstring() throws Exception {
        Path baseline = tempDir.resolve("issue-baseline.json");
        Files.writeString(baseline, """
                {
                  "rules": [
                    {
                      "status": "known_upstream",
                      "loggerPattern": "mixin*",
                      "messageContains": "Discarding @Unique public method",
                      "note": "Copycats mixin duplicate method spam."
                    }
                  ]
                }
                """);

        IssueBaselineConfig config = IssueBaselineConfig.load(baseline);
        IssueSnapshot issue = issue("WARN|mixin/|discarding @unique public method rotate", "mixin/",
                "Discarding @Unique public method rotate in copycats-neoforge.mixins.json");

        IssueDisposition disposition = config.dispositionFor(issue);

        assertEquals("known_upstream", disposition.status());
        assertEquals("Copycats mixin duplicate method spam.", disposition.note());
    }

    @Test
    void unmatchedIssuesRemainUnexpected() {
        IssueBaselineConfig config = new IssueBaselineConfig(List.of());
        IssueDisposition disposition = config.dispositionFor(issue("sig", "RecipeManager", "Error parsing recipe"));

        assertEquals("unexpected", disposition.status());
        assertEquals("", disposition.note());
    }

    private IssueSnapshot issue(String signature, String logger, String example) {
        Instant now = Instant.parse("2026-06-19T00:00:00Z");
        return new IssueSnapshot(
                signature,
                IssueCategory.UNKNOWN,
                Severity.WARN,
                1,
                now,
                now,
                Map.of(logger, 1),
                Map.of("unknown", 1),
                List.of(example),
                List.of()
        );
    }
}
