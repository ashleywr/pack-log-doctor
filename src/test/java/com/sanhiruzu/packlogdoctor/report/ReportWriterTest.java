package com.sanhiruzu.packlogdoctor.report;

import com.google.gson.JsonParser;
import com.sanhiruzu.packlogdoctor.analysis.FixSuggestion;
import com.sanhiruzu.packlogdoctor.analysis.IssueCategory;
import com.sanhiruzu.packlogdoctor.analysis.IssueSnapshot;
import com.sanhiruzu.packlogdoctor.analysis.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void appliesConfiguredLimitsToMarkdownAndJson() throws Exception {
        ReportConfig config = new ReportConfig(1, 1, 1, true, true);
        ReportWriter writer = new ReportWriter(tempDir, config);

        writer.write(snapshot());

        String markdown = Files.readString(tempDir.resolve("pack-log-doctor").resolve("report.md"));
        String html = Files.readString(tempDir.resolve("pack-log-doctor").resolve("report.html"));
        assertTrue(markdown.contains("## Dependency context"));
        assertTrue(markdown.contains("## Startup timings"));
        assertTrue(markdown.contains("Game Took"));
        assertTrue(markdown.contains("Largest quiet gaps"));
        assertTrue(markdown.contains("## Changed since last successful launch"));
        assertTrue(markdown.contains("old-disabled-mod.jar"));
        assertTrue(markdown.contains("Enabled state changed since the last successful launch."));
        assertTrue(markdown.contains("## Recently changed mod files"));
        assertTrue(markdown.contains("## Change suspects"));
        assertTrue(markdown.contains("tacz-neoforge.jar"));
        assertTrue(markdown.contains("gunsmithlib-neoforge.jar"));
        assertTrue(markdown.contains("### `examplemod` - Example Mod"));
        assertTrue(markdown.contains("`librarymod` REQUIRED [1.0,) [present] installed=1.2.0"));
        assertTrue(markdown.contains("ERROR x3"));
        assertFalse(markdown.contains("WARN x2"));
        assertTrue(markdown.contains("example one"));
        assertFalse(markdown.contains("example two"));
        assertFalse(markdown.contains("othermod"));
        assertTrue(html.contains("<title>Pack Log Doctor Report</title>"));
        assertTrue(html.contains("report-data"));
        assertTrue(html.contains("Copy fix note"));
        assertTrue(html.contains("Copy issue report"));
        assertTrue(html.contains("Open issue tracker"));
        assertTrue(html.contains("https://example.invalid/issues"));
        assertTrue(html.contains("Search GitHub issues"));
        assertTrue(html.contains("recipeMatches = exampleText.matchAll"));
        assertTrue(html.contains("Show action notes"));
        assertTrue(html.contains("Startup Timings"));
        assertTrue(html.contains("startupTimingHtml"));
        assertTrue(html.contains("Since Last Success"));
        assertTrue(html.contains("old-disabled-mod.jar"));
        assertTrue(Files.exists(tempDir.resolve("pack-log-doctor").resolve("reports").resolve("report-2026-06-06_09.00.00-test.html")));
        assertTrue(Files.exists(tempDir.resolve("pack-log-doctor").resolve("reports").resolve("report-2026-06-06_09.00.00-test.md")));
        assertTrue(Files.exists(tempDir.resolve("pack-log-doctor").resolve("reports").resolve("report-2026-06-06_09.00.00-test.json")));
        Path draft = tempDir.resolve("pack-log-doctor").resolve("drafts").resolve("draft-01-check-recipe.js");
        assertTrue(Files.exists(draft));
        assertTrue(Files.readString(draft).contains("event.remove({ id: 'examplemod:broken_recipe' });"));

        var json = JsonParser.parseString(Files.readString(tempDir.resolve("pack-log-doctor").resolve("report.json")))
                .getAsJsonObject();
        assertEquals(1, json.getAsJsonArray("issues").size());
        assertEquals(1, json.getAsJsonArray("issues").get(0).getAsJsonObject().getAsJsonArray("examples").size());
        assertEquals(1, json.getAsJsonObject("loudestMods").size());

        var agentPlan = JsonParser.parseString(Files.readString(tempDir.resolve("pack-log-doctor").resolve("agent-action-plan.json")))
                .getAsJsonObject();
        assertTrue(agentPlan.has("metadata"));
        assertTrue(agentPlan.has("summary"));
        assertTrue(agentPlan.has("actions"));
        assertTrue(agentPlan.has("environment"));
        var summary = agentPlan.getAsJsonObject("summary");
        assertEquals(1, summary.get("totalIssues").getAsInt());
        assertEquals(1, summary.get("actionableItems").getAsInt());
        var actions = agentPlan.getAsJsonArray("actions");
        assertEquals(1, actions.size());
        var action = actions.get(0).getAsJsonObject();
        assertTrue(action.has("priority"));
        assertTrue(action.has("type"));
        assertTrue(action.has("title"));
        assertTrue(action.has("confidence"));
        assertTrue(action.has("riskLevel"));
        assertTrue(action.has("successCriteria"));
        assertTrue(Files.exists(tempDir.resolve("pack-log-doctor").resolve("reports").resolve("report-2026-06-06_09.00.00-test-agent.json")));
    }

    @Test
    void writesUnexpectedReportFilteredByIssueBaseline() throws Exception {
        Path outputDir = tempDir.resolve("pack-log-doctor");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("issue-baseline.json"), """
                {
                  "rules": [
                    {
                      "status": "known_upstream",
                      "loggerPattern": "known.logger",
                      "messageContains": "known noisy warning",
                      "note": "Reviewed upstream noise."
                    }
                  ]
                }
                """);
        ReportWriter writer = new ReportWriter(tempDir, ReportConfig.defaults());
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        ReportSnapshot snapshot = new ReportSnapshot(
                now,
                "test",
                ReportEnvironment.unknown(),
                Set.of("knownmod", "newmod"),
                Map.of("knownmod", 3, "newmod", 1),
                StartupTimingReport.empty(),
                List.of(
                        issue("known-signature", "known.logger", "known noisy warning from upstream", "knownmod", 3),
                        issue("unexpected-signature", "new.logger", "new recipe error", "newmod", 1)
                )
        );

        writer.write(snapshot);

        String unexpectedMarkdown = Files.readString(outputDir.resolve("unexpected-report.md"));
        assertTrue(unexpectedMarkdown.contains("## Unexpected warnings and errors"));
        assertTrue(unexpectedMarkdown.contains("unexpected-signature"));
        assertTrue(unexpectedMarkdown.contains("new recipe error"));
        assertFalse(unexpectedMarkdown.contains("known-signature"));
        assertTrue(unexpectedMarkdown.contains("Known reviewed issues summarized"));
        assertTrue(unexpectedMarkdown.contains("known_upstream: 1 issue group(s), 3 event(s)"));

        var unexpectedJson = JsonParser.parseString(Files.readString(outputDir.resolve("unexpected-report.json")))
                .getAsJsonObject();
        assertEquals(1, unexpectedJson.getAsJsonArray("issues").size());
        assertEquals("unexpected-signature", unexpectedJson.getAsJsonArray("issues").get(0).getAsJsonObject()
                .get("signature").getAsString());
        assertEquals(3, unexpectedJson.getAsJsonObject("knownEventCounts").get("known_upstream").getAsInt());
    }

    @Test
    void keepsBaselineMatchedErrorsInUnexpectedReport() throws Exception {
        Path outputDir = tempDir.resolve("pack-log-doctor");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("issue-baseline.json"), """
                {
                  "rules": [
                    {
                      "status": "known_compat",
                      "loggerPattern": "known.error.logger",
                      "messageContains": "known recipe error",
                      "note": "Reviewed but still severe."
                    }
                  ]
                }
                """);
        ReportWriter writer = new ReportWriter(tempDir, ReportConfig.defaults());
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        ReportSnapshot snapshot = new ReportSnapshot(
                now,
                "test",
                ReportEnvironment.unknown(),
                Set.of("knownmod"),
                Map.of("knownmod", 1),
                StartupTimingReport.empty(),
                List.of(issue("known-error-signature", "known.error.logger", "known recipe error", "knownmod", 1, Severity.ERROR))
        );

        writer.write(snapshot);

        String unexpectedMarkdown = Files.readString(outputDir.resolve("unexpected-report.md"));
        assertTrue(unexpectedMarkdown.contains("known-error-signature"));
        assertTrue(unexpectedMarkdown.contains("known recipe error"));
        assertTrue(unexpectedMarkdown.contains("known_compat: 1 issue group(s), 1 event(s)"));

        var unexpectedJson = JsonParser.parseString(Files.readString(outputDir.resolve("unexpected-report.json")))
                .getAsJsonObject();
        assertEquals(1, unexpectedJson.getAsJsonArray("issues").size());
        assertEquals("known-error-signature", unexpectedJson.getAsJsonArray("issues").get(0).getAsJsonObject()
                .get("signature").getAsString());
        assertEquals(1, unexpectedJson.getAsJsonObject("knownEventCounts").get("known_compat").getAsInt());
    }

    @Test
    void doesNotSuggestLogSuppressionForErrorLoggers() throws Exception {
        ReportWriter writer = new ReportWriter(tempDir, ReportConfig.defaults());
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        ReportSnapshot snapshot = new ReportSnapshot(
                now,
                "test",
                ReportEnvironment.unknown(),
                Set.of("warnmod", "errormod"),
                Map.of("warnmod", 3, "errormod", 3),
                StartupTimingReport.empty(),
                List.of(
                        issue("known-warning-signature", "known.warning.logger", "known noisy warning", "warnmod", 3, Severity.WARN),
                        issue("known-error-signature", "known.error.logger", "known recipe error", "errormod", 3, Severity.ERROR)
                )
        );

        writer.write(snapshot);

        String suppressionConfig = Files.readString(tempDir.resolve("pack-log-doctor").resolve("suppression-config.json"));
        String log4jFilter = Files.readString(tempDir.resolve("pack-log-doctor").resolve("log4j2-filter.xml"));
        assertTrue(suppressionConfig.contains("known.warning.logger"));
        assertFalse(suppressionConfig.contains("known.error.logger"));
        assertTrue(log4jFilter.contains("known.warning.logger"));
        assertFalse(log4jFilter.contains("known.error.logger"));
    }

    @Test
    void writesUncaughtExceptionDiagnosticsToReport() throws Exception {
        ReportWriter writer = new ReportWriter(tempDir, ReportConfig.defaults());
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        ReportSnapshot snapshot = new ReportSnapshot(
                now,
                "uncaught-exception+latest-log",
                ReportEnvironment.unknown(),
                Set.of("lostcities", "c2me"),
                Map.of("lostcities", 1),
                StartupTimingReport.empty(),
                List.of(new UncaughtExceptionSnapshot(
                        now,
                        "c2me-worker-15",
                        "java.lang.IllegalStateException: Requested chunk unavailable during world generation",
                        "java.lang.IllegalStateException: Requested chunk unavailable during world generation\n"
                                + "\tat net.minecraft.server.level.WorldGenRegion.getChunk(WorldGenRegion.java:129)\n"
                                + "\tat mcjty.lostcities.worldgen.LostCityTerrainFeature.generate(LostCityTerrainFeature.java:354)"
                )),
                List.of()
        );

        writer.write(snapshot);

        String markdown = Files.readString(tempDir.resolve("pack-log-doctor").resolve("report.md"));
        String html = Files.readString(tempDir.resolve("pack-log-doctor").resolve("report.html"));
        assertTrue(markdown.contains("## Uncaught exceptions"));
        assertTrue(markdown.contains("c2me-worker-15"));
        assertTrue(markdown.contains("Requested chunk unavailable during world generation"));
        assertTrue(markdown.contains("LostCityTerrainFeature.generate"));
        assertTrue(html.contains("Uncaught Exceptions"));
        assertTrue(html.contains("LostCityTerrainFeature.generate"));

        var json = JsonParser.parseString(Files.readString(tempDir.resolve("pack-log-doctor").resolve("report.json")))
                .getAsJsonObject();
        assertEquals(1, json.getAsJsonArray("uncaughtExceptions").size());
        assertEquals("c2me-worker-15", json.getAsJsonArray("uncaughtExceptions")
                .get(0).getAsJsonObject().get("threadName").getAsString());
    }

    @Test
    void readsPackTrackedIssueBaselineFromConfigDirectory() throws Exception {
        Path configDir = tempDir.resolve("config");
        Files.createDirectories(configDir);
        Files.writeString(configDir.resolve("packlogdoctor-issue-baseline.json"), """
                {
                  "rules": [
                    {
                      "status": "known_pack_issue",
                      "loggerPattern": "known.logger",
                      "messageContains": "known noisy warning"
                    }
                  ]
                }
                """);
        ReportWriter writer = new ReportWriter(tempDir, ReportConfig.defaults());
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        writer.write(new ReportSnapshot(
                now,
                "test",
                ReportEnvironment.unknown(),
                Set.of("knownmod"),
                Map.of("knownmod", 1),
                StartupTimingReport.empty(),
                List.of(issue("known-signature", "known.logger", "known noisy warning", "knownmod", 1))
        ));

        String unexpectedMarkdown = Files.readString(tempDir.resolve("pack-log-doctor").resolve("unexpected-report.md"));
        assertTrue(unexpectedMarkdown.contains("No unexpected warnings or errors remain"));
        assertTrue(unexpectedMarkdown.contains("known_pack_issue: 1 issue group(s), 1 event(s)"));
    }

    private ReportSnapshot snapshot() {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        Map<String, Integer> loudestMods = new LinkedHashMap<>();
        loudestMods.put("examplemod", 5);
        loudestMods.put("othermod", 4);
        return new ReportSnapshot(
                now,
                "test",
                environment(),
                Set.of("examplemod", "othermod"),
                loudestMods,
                startupTimings(now),
                List.of(
                        new IssueSnapshot(
                                "sig-one",
                                IssueCategory.RECIPE,
                                Severity.ERROR,
                                3,
                                now,
                                now,
                                Map.of("logger", 3),
                                Map.of("examplemod", 3),
                                List.of("example one", "example two"),
                                List.of(new FixSuggestion(
                                        "kubejs-draft",
                                        "Check recipe",
                                        "Inspect the referenced recipe.",
                                        "",
                                        "Copy draft",
                                        "ServerEvents.recipes(event => {\n"
                                                + "  event.remove({ id: 'examplemod:broken_recipe' });\n"
                                                + "});\n"
                                ))
                        ),
                        new IssueSnapshot(
                                "sig-two",
                                IssueCategory.UNKNOWN,
                                Severity.WARN,
                                2,
                                now,
                                now,
                                Map.of("logger", 2),
                                Map.of("othermod", 2),
                                List.of("other example"),
                                List.of()
                        )
                )
        );
    }

    private StartupTimingReport startupTimings(Instant now) {
        return new StartupTimingReport(
                now,
                now.plusSeconds(10),
                10_000,
                List.of(new StartupTimingSnapshot(
                        now.plusSeconds(5),
                        "load-summary",
                        "Game Took",
                        "ModernFix",
                        "Render thread",
                        9_500,
                        "Game took 9.5 seconds to start"
                )),
                List.of(new StartupGapSnapshot(
                        now.plusSeconds(8),
                        3_000,
                        "Before logger: before message",
                        "After logger: after message"
                ))
        );
    }

    private IssueSnapshot issue(String signature, String logger, String example, String modId, int count) {
        return issue(signature, logger, example, modId, count, Severity.WARN);
    }

    private IssueSnapshot issue(String signature, String logger, String example, String modId, int count, Severity severity) {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        return new IssueSnapshot(
                signature,
                IssueCategory.UNKNOWN,
                severity,
                count,
                now,
                now,
                Map.of(logger, count),
                Map.of(modId, count),
                List.of(example),
                List.of()
        );
    }

    private ReportEnvironment environment() {
        Map<String, String> versions = new LinkedHashMap<>();
        versions.put("examplemod", "2.0.0");
        versions.put("librarymod", "1.2.0");

        Map<String, ModDependencySnapshot> dependencies = new LinkedHashMap<>();
        dependencies.put("examplemod", new ModDependencySnapshot(
                "examplemod",
                "Example Mod",
                "2.0.0",
                "examplemod.jar",
                "MOD",
                "https://example.invalid/issues",
                "https://example.invalid/project",
                "",
                List.of(new DependencySnapshot(
                        "librarymod",
                        "[1.0,)",
                        "REQUIRED",
                        "BOTH",
                        "NONE",
                        "1.2.0",
                        "present",
                        ""
                ))
        ));

        return new ReportEnvironment(
                "Test Pack",
                "1.0.0",
                "1.21.1",
                "21.1.228",
                "0.1.0",
                "21",
                "Test JVM",
                "Test OS",
                "1",
                "amd64",
                8,
                4096,
                2048,
                1024,
                versions,
                dependencies,
                List.of(
                        new ModFileSnapshot(
                                "packlogdoctor-neoforge.jar",
                                50_000,
                                Instant.parse("2026-06-06T13:33:13Z"),
                                Instant.parse("2026-06-06T13:33:13Z"),
                                true
                        ),
                        new ModFileSnapshot(
                                "tacz-neoforge.jar",
                                57_000_000,
                                Instant.parse("2026-06-06T13:08:31Z"),
                                Instant.parse("2026-06-06T13:08:33Z"),
                                true
                        ),
                        new ModFileSnapshot(
                                "gunsmithlib-neoforge.jar",
                                7_000_000,
                                Instant.parse("2026-06-06T13:08:31Z"),
                                Instant.parse("2026-06-06T13:08:32Z"),
                                true
                        )
                ),
                List.of(new ModFileChange(
                        "old-disabled-mod.jar",
                        "enabled",
                        "Enabled state changed since the last successful launch."
                ))
        );
    }
}
