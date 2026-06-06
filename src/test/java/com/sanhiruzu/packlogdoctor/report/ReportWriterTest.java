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
