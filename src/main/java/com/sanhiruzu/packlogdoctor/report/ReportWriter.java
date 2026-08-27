package com.sanhiruzu.packlogdoctor.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import com.sanhiruzu.packlogdoctor.analysis.IssueSnapshot;
import com.sanhiruzu.packlogdoctor.analysis.Severity;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReportWriter {
    private static final DateTimeFormatter ARCHIVE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss")
            .withZone(ZoneId.systemDefault());
    private final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>)
                    (value, type, context) -> new JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(value)))
            .setPrettyPrinting()
            .create();
    private final Path gameDir;
    private final Path outputDir;
    private final ReportConfig config;

    public ReportWriter(Path gameDir, ReportConfig config) {
        this.gameDir = gameDir;
        this.outputDir = gameDir.resolve("pack-log-doctor");
        this.config = config.sanitized();
    }

    public void write(ReportSnapshot snapshot) throws IOException {
        Files.createDirectories(outputDir);
        ReportSnapshot limited = limited(snapshot);
        writeDraftFiles(limited);
        Path archiveDir = outputDir.resolve("reports");
        Files.createDirectories(archiveDir);
        String archiveBaseName = "report-" + ARCHIVE_TIMESTAMP.format(limited.generatedAt())
                + "-" + safeTrigger(limited.trigger());
        if (config.writeJson()) {
            String json = gson.toJson(limited);
            writeLatestAndArchive(outputDir.resolve("report.json"), archiveDir.resolve(archiveBaseName + ".json"), json);
        }
        if (config.writeMarkdown()) {
            String markdown = markdown(limited);
            writeLatestAndArchive(outputDir.resolve("report.md"), archiveDir.resolve(archiveBaseName + ".md"), markdown);
        }
        if (Boolean.TRUE.equals(config.writeHtml())) {
            String html = html(limited);
            writeLatestAndArchive(outputDir.resolve("report.html"), archiveDir.resolve(archiveBaseName + ".html"), html);
        }
        writeUnexpectedReports(limited, archiveDir, archiveBaseName);
        String agentJson = agentActionPlan(limited);
        writeLatestAndArchive(outputDir.resolve("agent-action-plan.json"), archiveDir.resolve(archiveBaseName + "-agent.json"), agentJson);
        writeSuggestedSuppressions(limited);
        writeLog4j2Config();
    }

    public Path htmlPath() {
        return outputDir.resolve("report.html");
    }

    public Path archiveDir() {
        return outputDir.resolve("reports");
    }

    public Path draftsPath() {
        return outputDir.resolve("drafts").resolve("index.md");
    }

    private void writeLatestAndArchive(Path latestPath, Path archivePath, String content) throws IOException {
        Files.writeString(latestPath, content, StandardCharsets.UTF_8);
        Files.writeString(archivePath, content, StandardCharsets.UTF_8);
    }

    private void writeUnexpectedReports(ReportSnapshot snapshot, Path archiveDir, String archiveBaseName) throws IOException {
        IssueBaselineConfig baseline = loadIssueBaseline();
        UnexpectedReport unexpected = unexpectedReport(snapshot, baseline);
        String json = gson.toJson(unexpected.asJson());
        writeLatestAndArchive(
                outputDir.resolve("unexpected-report.json"),
                archiveDir.resolve(archiveBaseName + "-unexpected.json"),
                json
        );
        String markdown = unexpectedMarkdown(snapshot, unexpected);
        writeLatestAndArchive(
                outputDir.resolve("unexpected-report.md"),
                archiveDir.resolve(archiveBaseName + "-unexpected.md"),
                markdown
        );
    }

    private IssueBaselineConfig loadIssueBaseline() {
        Path local = outputDir.resolve("issue-baseline.json");
        if (Files.isRegularFile(local)) {
            return IssueBaselineConfig.load(local);
        }
        return IssueBaselineConfig.load(gameDir.resolve("config").resolve("packlogdoctor-issue-baseline.json"));
    }

    private UnexpectedReport unexpectedReport(ReportSnapshot snapshot, IssueBaselineConfig baseline) {
        List<IssueSnapshot> unexpectedIssues = new java.util.ArrayList<>();
        Map<String, Integer> knownIssueCounts = new LinkedHashMap<>();
        Map<String, Integer> knownEventCounts = new LinkedHashMap<>();
        Map<String, String> knownNotes = new LinkedHashMap<>();

        for (IssueSnapshot issue : snapshot.issues()) {
            IssueDisposition disposition = baseline.dispositionFor(issue);
            if (disposition.isUnexpected()) {
                unexpectedIssues.add(issue);
                continue;
            }
            if (shouldAlwaysSurface(issue)) {
                unexpectedIssues.add(issue);
            }
            knownIssueCounts.merge(disposition.status(), 1, Integer::sum);
            knownEventCounts.merge(disposition.status(), issue.count(), Integer::sum);
            if (!disposition.note().isBlank()) {
                knownNotes.putIfAbsent(disposition.status(), disposition.note());
            }
        }
        return new UnexpectedReport(unexpectedIssues, knownIssueCounts, knownEventCounts, knownNotes);
    }

    private boolean shouldAlwaysSurface(IssueSnapshot issue) {
        return issue.severity().isAtLeast(Severity.ERROR);
    }

    private String unexpectedMarkdown(ReportSnapshot snapshot, UnexpectedReport report) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Pack Log Doctor Unexpected Report\n\n");
        builder.append("- Generated: ").append(DateTimeFormatter.ISO_INSTANT.format(snapshot.generatedAt())).append('\n');
        builder.append("- Trigger: ").append(snapshot.trigger()).append('\n');
        builder.append("- Baseline: `pack-log-doctor/issue-baseline.json` or `config/packlogdoctor-issue-baseline.json`\n\n");

        builder.append("## Unexpected warnings and errors\n\n");
        if (report.issues().isEmpty()) {
            builder.append("No unexpected warnings or errors remain after applying the issue baseline.\n\n");
        } else {
            int index = 1;
            for (IssueSnapshot issue : report.issues()) {
                builder.append("### ").append(index++).append(". ")
                        .append(issue.severity()).append(" x").append(issue.count())
                        .append(" - ").append(issue.category()).append("\n\n");
                builder.append("- Signature: `").append(issue.signature()).append("`\n");
                builder.append("- Likely mods: ").append(formatCounts(issue.likelyModCounts())).append('\n');
                builder.append("- Loggers: ").append(formatCounts(issue.loggerCounts())).append("\n\n");
                if (!issue.examples().isEmpty()) {
                    builder.append("Examples:\n\n");
                    for (String example : issue.examples()) {
                        builder.append("```text\n").append(example).append("\n```\n\n");
                    }
                }
            }
        }

        builder.append("## Known reviewed issues summarized\n\n");
        if (report.knownIssueCounts().isEmpty()) {
            builder.append("No reviewed baseline rules matched this report.\n");
        } else {
            for (Map.Entry<String, Integer> entry : report.knownIssueCounts().entrySet()) {
                String status = entry.getKey();
                int events = report.knownEventCounts().getOrDefault(status, 0);
                builder.append("- ").append(status).append(": ")
                        .append(entry.getValue()).append(" issue group(s), ")
                        .append(events).append(" event(s)");
                String note = report.knownNotes().getOrDefault(status, "");
                if (!note.isBlank()) {
                    builder.append(" - ").append(note);
                }
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private void writeDraftFiles(ReportSnapshot snapshot) throws IOException {
        Path draftsDir = outputDir.resolve("drafts");
        Files.createDirectories(draftsDir);
        try (var files = Files.list(draftsDir)) {
            for (Path path : files.toList()) {
                String fileName = path.getFileName().toString();
                if (Files.isRegularFile(path)
                        && (fileName.startsWith("draft-") || fileName.equals("index.md"))) {
                    Files.delete(path);
                }
            }
        }

        StringBuilder index = new StringBuilder();
        index.append("# Pack Log Doctor Drafts\n\n");
        index.append("These files are generated outside active `kubejs/` so they do not change gameplay by themselves. ")
                .append("Review a draft before copying or moving it into the active pack.\n\n");

        int issueIndex = 1;
        int draftIndex = 1;
        for (IssueSnapshot issue : snapshot.issues()) {
            int suggestionIndex = 1;
            for (var suggestion : issue.suggestions()) {
                if (suggestion.actionBody().isBlank()) {
                    suggestionIndex++;
                    continue;
                }
                String extension = suggestion.actionBody().contains("ServerEvents.") ? ".js" : ".txt";
                String fileName = "draft-" + twoDigits(draftIndex) + "-"
                        + slug(suggestion.title()) + extension;
                Path draftPath = draftsDir.resolve(fileName);
                String body = "// Pack Log Doctor draft\n"
                        + "// Issue " + issueIndex + ": " + issue.category() + " " + issue.severity()
                        + " x" + issue.count() + "\n"
                        + "// Suggestion " + suggestionIndex + ": " + suggestion.title() + "\n\n"
                        + suggestion.actionBody();
                Files.writeString(draftPath, body, StandardCharsets.UTF_8);
                index.append("- `").append(fileName).append("`: ")
                        .append(suggestion.title())
                        .append(" (issue ").append(issueIndex).append(")\n");
                draftIndex++;
                suggestionIndex++;
            }
            issueIndex++;
        }

        if (draftIndex == 1) {
            index.append("No draft fixes were generated for this report.\n");
        }
        Files.writeString(draftsDir.resolve("index.md"), index.toString(), StandardCharsets.UTF_8);
    }

    private String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }

    private String slug(String value) {
        String slug = value == null ? "draft" : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) {
            return "draft";
        }
        return slug.length() > 64 ? slug.substring(0, 64).replaceAll("-+$", "") : slug;
    }

    private String safeTrigger(String trigger) {
        if (trigger == null || trigger.isBlank()) {
            return "unknown";
        }
        return trigger.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]+", "-");
    }

    private ReportSnapshot limited(ReportSnapshot snapshot) {
        List<IssueSnapshot> issues = snapshot.issues().stream()
                .limit(config.maxGroupedIssues())
                .map(this::limitIssue)
                .toList();
        return new ReportSnapshot(
                snapshot.generatedAt(),
                snapshot.trigger(),
                snapshot.environment(),
                snapshot.loadedModIds(),
                limitCounts(snapshot.loudestMods(), config.maxLoudestMods()),
                snapshot.startupTimings(),
                snapshot.uncaughtExceptions().stream()
                        .limit(config.maxExamplesPerIssue())
                        .toList(),
                issues
        );
    }

    private IssueSnapshot limitIssue(IssueSnapshot issue) {
        List<String> examples = issue.examples().stream()
                .limit(config.maxExamplesPerIssue())
                .toList();
        return new IssueSnapshot(
                issue.signature(),
                issue.category(),
                issue.severity(),
                issue.count(),
                issue.firstSeen(),
                issue.lastSeen(),
                issue.loggerCounts(),
                issue.likelyModCounts(),
                examples,
                issue.evidence(),
                issue.suggestions()
        );
    }

    private Map<String, Integer> limitCounts(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .limit(limit)
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private String agentActionPlan(ReportSnapshot snapshot) {
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("metadata", Map.of(
                "generatedAt", DateTimeFormatter.ISO_INSTANT.format(snapshot.generatedAt()),
                "trigger", snapshot.trigger(),
                "loadedModCount", snapshot.loadedModIds().size()
        ));

        List<Map<String, Object>> actions = new java.util.ArrayList<>();
        int priority = 1;

        for (IssueSnapshot issue : snapshot.issues()) {
            for (var suggestion : issue.suggestions()) {
                Map<String, Object> action = new LinkedHashMap<>();
                action.put("id", "action-" + twoDigits(priority));
                action.put("priority", priority++);
                action.put("type", actionType(suggestion.safety()));
                action.put("title", suggestion.title());
                action.put("issue", Map.of(
                        "category", issue.category().toString(),
                        "severity", issue.severity().toString(),
                        "count", issue.count(),
                        "signature", issue.signature()
                ));
                action.put("likelyMods", issue.likelyModCounts());
                action.put("confidence", estimateConfidence(issue, suggestion));
                action.put("riskLevel", safetyToRisk(suggestion.safety()));
                action.put("description", suggestion.detail());
                action.put("evidence", issue.evidence());
                if (!suggestion.url().isBlank()) {
                    action.put("referenceUrl", suggestion.url());
                }
                if (!suggestion.actionBody().isBlank()) {
                    action.put("actionBody", suggestion.actionBody());
                    action.put("actionLabel", suggestion.actionLabel().isBlank() ? "Apply" : suggestion.actionLabel());
                }
                action.put("successCriteria", successCriteria(issue.category().toString()));
                actions.add(action);
            }
        }

        plan.put("summary", Map.of(
                "totalIssues", snapshot.issues().size(),
                "actionableItems", actions.size(),
                "severityCounts", countBySeverity(snapshot.issues()),
                "topMod", snapshot.loudestMods().entrySet().stream()
                        .max((a, b) -> a.getValue().compareTo(b.getValue()))
                        .map(Map.Entry::getKey)
                        .orElse("unknown"),
                "recommendation", generateRecommendation(snapshot)
        ));

        if (actions.isEmpty()) {
            plan.put("actions", List.of());
        } else {
            plan.put("actions", actions.stream()
                    .sorted((a, b) -> Integer.compare(
                            (int) a.get("priority"),
                            (int) b.get("priority")
                    ))
                    .toList());
        }

        plan.put("environment", Map.of(
                "minecraftVersion", snapshot.environment().minecraftVersion(),
                "neoForgeVersion", snapshot.environment().neoForgeVersion(),
                "javaVersion", snapshot.environment().javaVersion(),
                "osName", snapshot.environment().osName(),
                "osVersion", snapshot.environment().osVersion()
        ));

        if (snapshot.startupTimings() != null && !snapshot.startupTimings().timings().isEmpty()) {
            plan.put("performanceIssues", snapshot.startupTimings().timings().stream()
                    .limit(5)
                    .map(t -> Map.of(
                            "duration", formatDuration(t.durationMs()),
                            "category", t.category(),
                            "label", t.label(),
                            "detail", t.detail()
                    ))
                    .toList());
        }

        return gson.toJson(plan);
    }

    private String actionType(String safety) {
        return switch (safety) {
            case "version-alignment" -> "update_dependency_version";
            case "known-issue" -> "wait_for_upstream_fix";
            case "read-only" -> "documentation_review";
            default -> safety.contains("draft") ? "apply_patch" : "investigate";
        };
    }

    private double estimateConfidence(IssueSnapshot issue, com.sanhiruzu.packlogdoctor.analysis.FixSuggestion suggestion) {
        if (issue.likelyModCounts().isEmpty()) {
            return 0.5;
        }
        int total = issue.likelyModCounts().values().stream().mapToInt(Integer::intValue).sum();
        int topMod = issue.likelyModCounts().values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double modConfidence = (double) topMod / total;
        double suggestionBonus = suggestion.url().isBlank() ? 0 : 0.1;
        return Math.min(1.0, modConfidence + suggestionBonus);
    }

    private String safetyToRisk(String safety) {
        return switch (safety) {
            case "read-only" -> "low";
            case "version-alignment" -> "low";
            case "known-issue" -> "low";
            default -> safety.contains("draft") ? "medium" : "unknown";
        };
    }

    private String successCriteria(String category) {
        return switch (category) {
            case "PERFORMANCE" -> "Reduce log volume or startup time without new errors";
            case "FEATURE_DEGRADATION" -> "Restore missing functionality or recipe availability";
            case "COMPATIBILITY" -> "Resolve version conflicts or dependency mismatches";
            case "MISSING_DATA" -> "Provide missing mod data, assets, or configuration";
            default -> "Eliminate this category of warning from logs";
        };
    }

    private String generateRecommendation(ReportSnapshot snapshot) {
        int criticalCount = (int) snapshot.issues().stream()
                .filter(i -> i.severity().toString().equals("ERROR") || i.severity().toString().equals("FATAL"))
                .count();
        if (criticalCount > 0) {
            return "Address " + criticalCount + " critical error(s) immediately before further testing";
        }
        if (!snapshot.issues().isEmpty()) {
            return "Review and apply suggested fixes in priority order; test after each major change";
        }
        return "No actionable issues detected";
    }

    private Map<String, Integer> countBySeverity(List<IssueSnapshot> issues) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (IssueSnapshot issue : issues) {
            String sev = issue.severity().toString();
            counts.put(sev, counts.getOrDefault(sev, 0) + 1);
        }
        return counts;
    }

    private String html(ReportSnapshot snapshot) {
        String json = gson.toJson(snapshot)
                .replace("<", "\\u003c")
                .replace(">", "\\u003e")
                .replace("&", "\\u0026");
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Pack Log Doctor Report</title>
                  <style>
                    :root { color-scheme: light dark; --bg: #f7f7f4; --panel: #ffffff; --ink: #171717; --muted: #666; --line: #d9d7cf; --accent: #096b72; --danger: #a42727; --warn: #9a5b00; --ok: #2f6f3e; }
                    @media (prefers-color-scheme: dark) { :root { --bg: #111313; --panel: #1b1f1f; --ink: #ecebea; --muted: #aaa; --line: #343939; --accent: #66c6cf; --danger: #ff8f8f; --warn: #ffc46b; --ok: #8fd69e; } }
                    * { box-sizing: border-box; }
                    body { margin: 0; font: 14px/1.45 system-ui, Segoe UI, sans-serif; background: var(--bg); color: var(--ink); }
                    header { position: sticky; top: 0; z-index: 2; background: color-mix(in srgb, var(--bg) 92%, transparent); border-bottom: 1px solid var(--line); backdrop-filter: blur(10px); }
                    .wrap { max-width: 1280px; margin: 0 auto; padding: 16px; }
                    h1 { font-size: 22px; margin: 0 0 6px; }
                    h2 { font-size: 16px; margin: 0 0 10px; }
                    .meta, .muted { color: var(--muted); }
                    .summary { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; margin-top: 12px; }
                    .stat, .panel, .issue { background: var(--panel); border: 1px solid var(--line); border-radius: 8px; }
                    .stat { padding: 10px; }
                    .stat strong { display: block; font-size: 20px; }
                    .grid { display: grid; grid-template-columns: 310px minmax(0, 1fr); gap: 14px; align-items: start; }
                    aside { position: sticky; top: 132px; }
                    .panel { padding: 12px; margin-bottom: 12px; }
                    .controls { display: grid; gap: 8px; }
                    input, select, button { font: inherit; }
                    input, select { width: 100%; padding: 8px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel); color: var(--ink); }
                    button { border: 1px solid var(--line); border-radius: 6px; background: var(--panel); color: var(--ink); padding: 7px 9px; cursor: pointer; }
                    button:hover { border-color: var(--accent); }
                    a.button { display: inline-block; border: 1px solid var(--line); border-radius: 6px; color: var(--ink); padding: 7px 9px; text-decoration: none; }
                    a.button:hover { border-color: var(--accent); }
                    .chips { display: flex; flex-wrap: wrap; gap: 6px; }
                    .chip { border: 1px solid var(--line); border-radius: 999px; padding: 4px 8px; color: var(--muted); }
                    .issue { margin-bottom: 10px; overflow: hidden; }
                    .issue-head { display: grid; grid-template-columns: auto 1fr auto; gap: 10px; padding: 12px; cursor: pointer; align-items: start; }
                    .badge { border-radius: 999px; padding: 4px 8px; font-weight: 700; border: 1px solid var(--line); white-space: nowrap; }
                    .ERROR, .FATAL { color: var(--danger); }
                    .WARN { color: var(--warn); }
                    .cat { color: var(--accent); font-weight: 700; }
                    .issue-body { display: none; padding: 0 12px 12px; border-top: 1px solid var(--line); }
                    .issue.open .issue-body { display: block; }
                    pre { overflow: auto; padding: 10px; background: color-mix(in srgb, var(--panel) 75%, var(--bg)); border: 1px solid var(--line); border-radius: 6px; white-space: pre-wrap; }
                    code { font-family: ui-monospace, SFMono-Regular, Consolas, monospace; font-size: 12px; }
                    .actions { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 10px; }
                    .suggestion { border-left: 3px solid var(--accent); padding: 8px 10px; margin: 8px 0; background: color-mix(in srgb, var(--panel) 88%, var(--accent)); }
                    .suggestion .title-row { display: flex; flex-wrap: wrap; gap: 8px; align-items: baseline; }
                    .split { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
                    textarea { width: 100%; min-height: 160px; padding: 10px; border: 1px solid var(--line); border-radius: 6px; background: var(--panel); color: var(--ink); font: 12px/1.4 ui-monospace, SFMono-Regular, Consolas, monospace; }
                    ul { padding-left: 18px; }
                    @media (max-width: 900px) { .summary, .grid, .split { grid-template-columns: 1fr; } aside { position: static; } header { position: static; } }
                  </style>
                </head>
                <body>
                <header><div class="wrap">
                  <h1>Pack Log Doctor Report</h1>
                  <div class="meta" id="meta"></div>
                  <div class="summary" id="summary"></div>
                </div></header>
                <main class="wrap grid">
                  <aside>
                    <section class="panel controls">
                      <h2>Filter</h2>
                      <input id="search" placeholder="Search issues, mods, evidence">
                      <select id="severity"><option value="">All severities</option></select>
                      <select id="category"><option value="">All categories</option></select>
                      <select id="mod"><option value="">All likely mods</option></select>
                      <button id="expand">Expand visible</button>
                      <button id="collapse">Collapse all</button>
                    </section>
                    <section class="panel">
                      <h2>Loudest Mods</h2>
                      <div class="chips" id="loudest"></div>
                    </section>
                    <section class="panel">
                      <h2>Since Last Success</h2>
                      <ul id="changes"></ul>
                    </section>
                    <section class="panel">
                      <h2>Recent Files</h2>
                      <ul id="recent"></ul>
                    </section>
                    <section class="panel">
                      <h2>Provenance</h2>
                      <ul id="provenance"></ul>
                    </section>
                  </aside>
                  <section>
                    <div class="panel split">
                      <div><h2>Environment</h2><div id="environment"></div></div>
                      <div><h2>Next Actions</h2><div id="nextActions" class="muted"></div></div>
                    </div>
                    <div class="panel">
                      <h2>Startup Timings</h2>
                      <div id="startupTimings" class="muted"></div>
                    </div>
                    <div id="uncaughtExceptions"></div>
                    <div id="issues"></div>
                  </section>
                </main>
                <script type="application/json" id="report-data">
                """ + json + """
                </script>
                <script>
                const report = JSON.parse(document.getElementById('report-data').textContent);
                const $ = id => document.getElementById(id);
                const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
                const counts = o => Object.entries(o || {}).map(([k,v]) => `${k}=${v}`).join(', ') || 'none';
                const issueText = issue => [issue.signature, issue.category, issue.severity, ...Object.keys(issue.likelyModCounts || {}), ...(issue.evidence || []), ...(issue.examples || [])].join('\\n').toLowerCase();
                function optionize(id, values) { for (const value of [...values].sort()) $(id).insertAdjacentHTML('beforeend', `<option value="${esc(value)}">${esc(value)}</option>`); }
                function topMod(issue) { return Object.keys(issue.likelyModCounts || {})[0] || 'unknown'; }
                function modInfo(modId) { return (report.environment.modDependencies || {})[modId] || null; }
                function trackerUrl(issue) {
                  const info = modInfo(topMod(issue));
                  return info && info.issueTrackerUrl ? info.issueTrackerUrl : '';
                }
                function projectUrl(issue) {
                  const info = modInfo(topMod(issue));
                  return info && info.displayUrl ? info.displayUrl : '';
                }
                function actionText(issue, suggestion) {
                  const mod = topMod(issue);
                  const lines = [`Issue: ${issue.severity} x${issue.count} ${issue.category}`, `Likely mod: ${mod}`, `Suggestion: ${suggestion.title}`, suggestion.detail];
                  if (suggestion.url) lines.push(`Reference: ${suggestion.url}`);
                  if (suggestion.safety === 'version-alignment') lines.push(`Action: align versions for ${Object.keys(issue.likelyModCounts || {}).join(', ')}`);
                  if (suggestion.safety === 'known-issue' || suggestion.safety === 'read-only') lines.push('Action: read the linked/details evidence before disabling mods.');
                  if (suggestion.safety.includes('draft')) lines.push('Action: draft a datapack/KubeJS patch only after confirming the recipe or tag ID.');
                  if (suggestion.actionBody) lines.push('', suggestion.actionBody);
                  return lines.join('\\n');
                }
                function uncaughtText(exception) {
                  return [
                    `Thread: ${exception.threadName || 'unknown'}`,
                    `Time: ${exception.timestamp || 'unknown'}`,
                    `Message: ${exception.message || ''}`,
                    '',
                    exception.stackTrace || ''
                  ].join('\\n');
                }
                function issueReportText(issue) {
                  return [
                    `Title: ${topMod(issue)} ${issue.category}: ${issue.signature}`,
                    '',
                    'What happened:',
                    `Pack Log Doctor grouped ${issue.count} ${issue.severity} log entries as ${issue.category}.`,
                    '',
                    'Likely mod ownership:',
                    counts(issue.likelyModCounts),
                    '',
                    'Evidence:',
                    ...(issue.evidence || ['No structured evidence captured.']).map(e => `- ${e}`),
                    '',
                    'Examples:',
                    ...(issue.examples || []).map(e => `---\\n${e}`),
                    '',
                    'Environment:',
                    `Minecraft ${report.environment.minecraftVersion}`,
                    `NeoForge ${report.environment.neoForgeVersion}`,
                    `Java ${report.environment.javaVersion}`,
                    `Pack Log Doctor trigger ${report.trigger}`
                  ].join('\\n');
                }
                function evidenceTerms(issue) {
                  const evidence = issue.evidence || [];
                  const preferredLabels = [
                    'Recipe id',
                    'Missing registry key',
                    'Missing class',
                    'Dependency',
                    'Requested by',
                    'Registry element',
                    'Missing loot table',
                    'Blockstate file',
                    'Missing model variant',
                    'Invalid pack path',
                    'Mixin config',
                    'Mixin owner',
                    'Top hang suspect',
                    'Guidebook missing recipe',
                    'Missing sound event',
                    'Shader program'
                  ];
                  const terms = [];
                  for (const label of preferredLabels) {
                    const prefix = `${label}: `;
                    for (const value of evidence.filter(e => String(e).startsWith(prefix)).map(e => String(e).slice(prefix.length))) {
                      if (value && !terms.includes(value)) terms.push(value);
                      if (terms.length >= 3) return terms;
                    }
                  }
                  const exampleText = (issue.examples || []).join('\\n');
                  const recipeMatches = exampleText.matchAll(/\\bRecipe\\s+([a-z0-9_.-]+:[a-z0-9_./-]+)\\s+\\(of type ([^)]+)\\) not found\\b/gi);
                  for (const match of recipeMatches) {
                    if (!terms.includes(match[1])) terms.push(match[1]);
                    if (!terms.includes(match[2])) terms.push(match[2]);
                    if (terms.length >= 3) return terms;
                  }
                  const resourceMatches = exampleText.matchAll(/\\b([a-z0-9_.-]+:[a-z0-9_./-]+)\\b/gi);
                  for (const match of resourceMatches) {
                    if (!terms.includes(match[1])) terms.push(match[1]);
                    if (terms.length >= 3) return terms;
                  }
                  return terms;
                }
                function githubIssueSearch(issue) {
                  const terms = evidenceTerms(issue);
                  const query = terms.length
                    ? [topMod(issue), issue.severity, ...terms].join(' ')
                    : [topMod(issue), issue.severity, issue.category, issue.signature].join(' ');
                  return `https://github.com/search?q=${encodeURIComponent(query)}&type=issues`;
                }
                function render() {
                  $('meta').textContent = `Generated ${report.generatedAt} | trigger ${report.trigger} | ${report.loadedModIds.length} loaded mods`;
                  const bySeverity = report.issues.reduce((map, issue) => ((map[issue.severity] ||= []).push(issue), map), {});
                  const byCategory = report.issues.reduce((map, issue) => ((map[issue.category] ||= []).push(issue), map), {});
                  $('summary').innerHTML = [
                    ['Issues', report.issues.length],
                    ['Errors', (bySeverity.ERROR || []).length + (bySeverity.FATAL || []).length],
                    ['Performance', (byCategory.PERFORMANCE || []).length],
                    ['Feature gaps', (byCategory.FEATURE_DEGRADATION || []).length],
                    ['Top mod', Object.keys(report.loudestMods || {})[0] || 'none']
                  ].map(([k,v]) => `<div class="stat"><span class="muted">${esc(k)}</span><strong>${esc(v)}</strong></div>`).join('');
                  $('environment').innerHTML = `<div>Minecraft ${esc(report.environment.minecraftVersion)} | NeoForge ${esc(report.environment.neoForgeVersion)}</div><div>Java ${esc(report.environment.javaVersion)}</div><div>${esc(report.environment.osName)} ${esc(report.environment.osVersion)}</div>`;
                  $('loudest').innerHTML = Object.entries(report.loudestMods || {}).map(([k,v]) => `<button class="chip" data-mod="${esc(k)}">${esc(k)} ${v}</button>`).join('');
                  $('changes').innerHTML = (report.environment.modFileChangesSinceLastSuccess || []).slice(0, 12).map(c => `<li><code>${esc(c.fileName)}</code><br><span class="muted">${esc(c.changeType)} - ${esc(c.detail)}</span></li>`).join('') || '<li class="muted">No baseline changes found.</li>';
                  $('recent').innerHTML = (report.environment.modFiles || []).slice(0, 8).map(f => `<li><code>${esc(f.fileName)}</code>${f.enabled ? '' : ' <span class="muted">disabled</span>'}</li>`).join('');
                  $('provenance').innerHTML = (report.environment.modProvenance || []).slice(0, 10).map(p => `<li><code>${esc(p.fileName)}</code><br><span class="muted">${esc(p.confidence)}${p.modrinthHash ? ' | Modrinth hash' : ''}${p.curseForgeFingerprint ? ' | CurseForge fingerprint' : ''}</span></li>`).join('') || '<li class="muted">No provenance hints found.</li>';
                  $('startupTimings').innerHTML = startupTimingHtml();
                  $('uncaughtExceptions').innerHTML = uncaughtExceptionHtml();
                  optionize('severity', new Set(report.issues.map(i => i.severity)));
                  optionize('category', new Set(report.issues.map(i => i.category)));
                  optionize('mod', new Set(report.issues.flatMap(i => Object.keys(i.likelyModCounts || {}))));
                  $('nextActions').innerHTML = nextActions();
                  renderIssues();
                }
                function nextActions() {
                  const first = report.issues.slice(0, 5).map(i => `<li><strong>${esc(i.category)}</strong>: ${esc(topMod(i))} (${esc(i.severity)} x${i.count})</li>`).join('');
                  return `<ul>${first}</ul><p>Generated fix drafts are written to <code>pack-log-doctor/drafts/</code> when available.</p><button id="showActions">Show action notes</button><div id="actionNotes" style="display:none;margin-top:10px"><textarea readonly></textarea><div class="actions"><button id="copyActions">Copy all action notes</button></div></div>`;
                }
                function startupTimingHtml() {
                  const data = report.startupTimings || {};
                  const timings = (data.timings || []).slice(0, 10);
                  const gaps = (data.gaps || []).slice(0, 6);
                  if (!timings.length && !gaps.length) return 'No startup timing events captured yet.';
                  const timingItems = timings.map(t => `<li><strong>${esc(formatMs(t.durationMs))}</strong> <code>${esc(t.category)}</code> ${esc(t.label)}<br><span class="muted">${esc(t.detail)}</span></li>`).join('');
                  const gapItems = gaps.map(g => `<li><strong>${esc(formatMs(g.durationMs))}</strong><br><span class="muted">Before: ${esc(g.before)}</span><br><span class="muted">After: ${esc(g.after)}</span></li>`).join('');
                  return `${timingItems ? `<h2>Explicit durations</h2><ul>${timingItems}</ul>` : ''}${gapItems ? `<h2>Largest log gaps</h2><ul>${gapItems}</ul>` : ''}`;
                }
                function uncaughtExceptionHtml() {
                  const exceptions = report.uncaughtExceptions || [];
                  if (!exceptions.length) return '';
                  return `<section class="panel"><h2>Uncaught Exceptions</h2><p class="muted">These crash-triggering exceptions are shown outside grouped log filtering.</p>${exceptions.map((exception, index) => `
                    <article class="issue open">
                      <div class="issue-head">
                        <span class="badge FATAL">FATAL</span>
                        <div><div><span class="cat">Uncaught</span> ${esc(exception.threadName || 'unknown thread')}</div><div class="muted">${esc(exception.message || '')}</div></div>
                        <span class="muted">#${index + 1}</span>
                      </div>
                      <div class="issue-body">
                        <pre><code>${esc(exception.stackTrace || exception.message || '')}</code></pre>
                        <div class="actions"><button data-copy="${esc(uncaughtText(exception))}">Copy exception</button></div>
                      </div>
                    </article>`).join('')}</section>`;
                }
                function formatMs(ms) {
                  const value = Number(ms || 0);
                  return value >= 1000 ? `${(value / 1000).toFixed(2)}s` : `${value}ms`;
                }
                function renderIssues() {
                  const q = $('search').value.toLowerCase();
                  const sev = $('severity').value, cat = $('category').value, mod = $('mod').value;
                  const visible = report.issues.filter(i => (!sev || i.severity === sev) && (!cat || i.category === cat) && (!mod || Object.hasOwn(i.likelyModCounts || {}, mod)) && (!q || issueText(i).includes(q)));
                  $('issues').innerHTML = visible.map((issue, idx) => `
                    <article class="issue" data-index="${report.issues.indexOf(issue)}">
                      <div class="issue-head">
                        <span class="badge ${esc(issue.severity)}">${esc(issue.severity)} x${issue.count}</span>
                        <div><div><span class="cat">${esc(issue.category)}</span> ${esc(topMod(issue))}</div><div class="muted">${esc(issue.signature)}</div></div>
                        <span class="muted">#${idx + 1}</span>
                      </div>
                      <div class="issue-body">
                        <div class="split"><div><strong>Likely mods</strong><p>${esc(counts(issue.likelyModCounts))}</p></div><div><strong>Loggers</strong><p>${esc(counts(issue.loggerCounts))}</p></div></div>
                        ${(issue.evidence || []).length ? `<h2>Evidence</h2><ul>${issue.evidence.map(e => `<li>${esc(e)}</li>`).join('')}</ul>` : ''}
                        ${(issue.examples || []).length ? `<h2>Examples</h2>${issue.examples.map(e => `<pre><code>${esc(e)}</code></pre>`).join('')}` : ''}
                        ${(issue.suggestions || []).length ? `<h2>Suggestions</h2>${issue.suggestions.map(s => `<div class="suggestion"><div class="title-row"><strong>${esc(s.title)}</strong> <span class="muted">${esc(s.safety)}</span></div><div>${esc(s.detail)}</div><div class="actions">${s.url ? `<a class="button" href="${esc(s.url)}" target="_blank" rel="noreferrer">Open reference</a>` : ''}<button data-copy="${esc(actionText(issue, s))}">Copy fix note</button>${s.actionBody ? `<button data-copy="${esc(s.actionBody)}">${esc(s.actionLabel || 'Copy draft')}</button>` : ''}</div></div>`).join('')}` : ''}
                        <div class="actions"><button data-copy="${esc(issue.signature)}">Copy signature</button><button data-copy="${esc(issueReportText(issue))}">Copy issue report</button>${trackerUrl(issue) ? `<a class="button" href="${esc(trackerUrl(issue))}" target="_blank" rel="noreferrer">Open issue tracker</a>` : ''}${projectUrl(issue) ? `<a class="button" href="${esc(projectUrl(issue))}" target="_blank" rel="noreferrer">Open project page</a>` : ''}<a class="button" href="${esc(githubIssueSearch(issue))}" target="_blank" rel="noreferrer">Search GitHub issues</a></div>
                      </div>
                    </article>`).join('') || '<section class="panel">No issues match the current filters.</section>';
                }
                document.addEventListener('click', event => {
                  const head = event.target.closest('.issue-head');
                  if (head) head.closest('.issue').classList.toggle('open');
                  const mod = event.target.dataset.mod;
                  if (mod) { $('mod').value = mod; renderIssues(); }
                  const copy = event.target.dataset.copy;
                  if (copy) copyText(copy);
                  if (event.target.id === 'showActions') {
                    const text = report.issues.flatMap(i => (i.suggestions || []).map(s => actionText(i, s))).join('\\n\\n---\\n\\n');
                    const panel = $('actionNotes');
                    panel.style.display = panel.style.display === 'none' ? 'block' : 'none';
                    panel.querySelector('textarea').value = text;
                  }
                  if (event.target.id === 'copyActions') {
                    copyText($('actionNotes').querySelector('textarea').value);
                  }
                });
                ['search','severity','category','mod'].forEach(id => $(id).addEventListener('input', renderIssues));
                $('expand').addEventListener('click', () => document.querySelectorAll('.issue').forEach(e => e.classList.add('open')));
                $('collapse').addEventListener('click', () => document.querySelectorAll('.issue').forEach(e => e.classList.remove('open')));
                function copyText(text) {
                  if (navigator.clipboard?.writeText) {
                    navigator.clipboard.writeText(text).catch(() => fallbackCopy(text));
                  } else {
                    fallbackCopy(text);
                  }
                }
                function fallbackCopy(text) {
                  const area = document.createElement('textarea');
                  area.value = text;
                  area.style.position = 'fixed';
                  area.style.left = '-1000px';
                  document.body.appendChild(area);
                  area.select();
                  document.execCommand('copy');
                  area.remove();
                }
                render();
                </script>
                </body>
                </html>
                """;
    }

    private String markdown(ReportSnapshot snapshot) {
        StringBuilder builder = new StringBuilder();
        builder.append("# Pack Log Doctor Report\n\n");
        builder.append("- Generated: ").append(DateTimeFormatter.ISO_INSTANT.format(snapshot.generatedAt())).append('\n');
        builder.append("- Trigger: ").append(snapshot.trigger()).append('\n');
        builder.append("- Loaded mods known to analyzer: ").append(snapshot.loadedModIds().size()).append("\n\n");

        appendEnvironment(builder, snapshot.environment());
        appendStartupTimings(builder, snapshot.startupTimings());
        appendModFileChanges(builder, snapshot.environment());
        appendRecentModFiles(builder, snapshot.environment());
        appendProvenance(builder, snapshot.environment());
        appendChangeSuspects(builder, snapshot.environment());
        appendDependencyContext(builder, snapshot);
        appendUncaughtExceptions(builder, snapshot.uncaughtExceptions());

        builder.append("## Loudest likely mods\n\n");
        if (snapshot.loudestMods().isEmpty()) {
            builder.append("No likely mod ownership identified yet.\n\n");
        } else {
            for (Map.Entry<String, Integer> entry : snapshot.loudestMods().entrySet()) {
                builder.append("- `").append(entry.getKey()).append("`: ").append(entry.getValue()).append('\n');
            }
            builder.append('\n');
        }

        builder.append("## Grouped warnings and errors\n\n");
        if (snapshot.issues().isEmpty()) {
            builder.append("No warnings or errors captured after Pack Log Doctor loaded.\n");
            return builder.toString();
        }

        int index = 1;
        for (IssueSnapshot issue : snapshot.issues()) {
            builder.append("### ").append(index++).append(". ")
                    .append(issue.severity()).append(" x").append(issue.count())
                    .append(" - ").append(issue.category()).append("\n\n");
            builder.append("- Signature: `").append(issue.signature()).append("`\n");
            builder.append("- First seen: ").append(DateTimeFormatter.ISO_INSTANT.format(issue.firstSeen())).append('\n');
            builder.append("- Last seen: ").append(DateTimeFormatter.ISO_INSTANT.format(issue.lastSeen())).append('\n');
            builder.append("- Likely mods: ").append(formatCounts(issue.likelyModCounts())).append('\n');
            builder.append("- Loggers: ").append(formatCounts(issue.loggerCounts())).append("\n\n");

            if (!issue.examples().isEmpty()) {
                builder.append("Examples:\n\n");
                for (String example : issue.examples()) {
                    builder.append("```text\n").append(example).append("\n```\n\n");
                }
            }

            if (!issue.evidence().isEmpty()) {
                builder.append("Evidence:\n\n");
                for (String evidence : issue.evidence()) {
                    builder.append("- ").append(evidence).append('\n');
                }
                builder.append('\n');
            }

            if (!issue.suggestions().isEmpty()) {
                builder.append("Suggestions:\n\n");
                issue.suggestions().forEach(suggestion -> {
                    builder.append("- `")
                            .append(suggestion.safety()).append("`: ")
                            .append(suggestion.title()).append(" - ")
                            .append(suggestion.detail()).append('\n');
                    if (!suggestion.url().isBlank()) {
                        builder.append("  Reference: ").append(suggestion.url()).append('\n');
                    }
                    if (!suggestion.actionBody().isBlank()) {
                        builder.append("  ").append(suggestion.actionLabel().isBlank() ? "Draft" : suggestion.actionLabel()).append(":\n\n");
                        builder.append("```js\n").append(suggestion.actionBody()).append("```\n\n");
                    }
                });
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private void appendUncaughtExceptions(StringBuilder builder, List<UncaughtExceptionSnapshot> uncaughtExceptions) {
        if (uncaughtExceptions == null || uncaughtExceptions.isEmpty()) {
            return;
        }

        builder.append("## Uncaught exceptions\n\n");
        builder.append("These are the exception(s) that triggered terminal report writing. ")
                .append("They are shown separately from grouped log noise so crash stacks are not hidden by baseline filtering.\n\n");
        int index = 1;
        for (UncaughtExceptionSnapshot exception : uncaughtExceptions) {
            builder.append("### Exception ").append(index++).append("\n\n");
            builder.append("- Time: ").append(DateTimeFormatter.ISO_INSTANT.format(exception.timestamp())).append('\n');
            builder.append("- Thread: `").append(exception.threadName()).append("`\n");
            builder.append("- Message: `").append(exception.message()).append("`\n\n");
            if (!exception.stackTrace().isBlank()) {
                builder.append("```text\n").append(exception.stackTrace()).append("\n```\n\n");
            }
        }
    }

    private void appendEnvironment(StringBuilder builder, ReportEnvironment environment) {
        builder.append("## Environment\n\n");
        appendOptional(builder, "Modpack", environment.modpackName());
        appendOptional(builder, "Modpack version", environment.modpackVersion());
        builder.append("- Minecraft: ").append(environment.minecraftVersion()).append('\n');
        builder.append("- NeoForge: ").append(environment.neoForgeVersion()).append('\n');
        builder.append("- Pack Log Doctor: ").append(environment.packLogDoctorVersion()).append('\n');
        builder.append("- Java: ").append(environment.javaVersion()).append(" (").append(environment.javaVendor()).append(")\n");
        builder.append("- OS: ").append(environment.osName()).append(' ')
                .append(environment.osVersion()).append(" (").append(environment.osArch()).append(")\n");
        builder.append("- CPUs: ").append(environment.availableProcessors()).append('\n');
        builder.append("- Memory: max ").append(environment.maxMemoryMb()).append(" MiB, total ")
                .append(environment.totalMemoryMb()).append(" MiB, free ")
                .append(environment.freeMemoryMb()).append(" MiB\n\n");
    }

    private void appendOptional(StringBuilder builder, String label, String value) {
        if (value != null && !value.isBlank()) {
            builder.append("- ").append(label).append(": ").append(value).append('\n');
        }
    }

    private void appendStartupTimings(StringBuilder builder, StartupTimingReport timings) {
        if (timings == null || (timings.timings().isEmpty() && timings.gaps().isEmpty())) {
            return;
        }

        builder.append("## Startup timings\n\n");
        if (timings.observedDurationMs() > 0L) {
            builder.append("- Observed log window: ").append(formatDuration(timings.observedDurationMs())).append('\n');
        }
        if (!timings.timings().isEmpty()) {
            builder.append("- Slow explicit timings:\n");
            timings.timings().stream()
                    .limit(12)
                    .forEach(timing -> builder.append("  - ")
                            .append(formatDuration(timing.durationMs()))
                            .append(" `").append(timing.category()).append("` ")
                            .append(timing.label())
                            .append(" - ").append(timing.detail())
                            .append('\n'));
        }
        if (!timings.gaps().isEmpty()) {
            builder.append("- Largest quiet gaps between log events:\n");
            timings.gaps().stream()
                    .limit(8)
                    .forEach(gap -> builder.append("  - ")
                            .append(formatDuration(gap.durationMs()))
                            .append(" after `").append(gap.before()).append("`; next `")
                            .append(gap.after()).append("`\n"));
        }
        builder.append('\n');
    }

    private String formatDuration(long durationMs) {
        if (durationMs >= 1000L) {
            return String.format(java.util.Locale.ROOT, "%.2fs", durationMs / 1000.0);
        }
        return durationMs + "ms";
    }

    private void appendModFileChanges(StringBuilder builder, ReportEnvironment environment) {
        if (environment.modFileChangesSinceLastSuccess().isEmpty()) {
            return;
        }

        builder.append("## Changed since last successful launch\n\n");
        builder.append("These changes are based on Pack Log Doctor's last startup-complete baseline, not filesystem timestamps.\n\n");
        environment.modFileChangesSinceLastSuccess().stream()
                .limit(40)
                .forEach(change -> builder.append("- `")
                        .append(change.fileName()).append("` ")
                        .append(change.changeType()).append(" - ")
                        .append(change.detail()).append('\n'));
        builder.append('\n');
    }

    private void appendRecentModFiles(StringBuilder builder, ReportEnvironment environment) {
        if (environment.modFiles().isEmpty()) {
            return;
        }

        builder.append("## Recently changed mod files\n\n");
        environment.modFiles().stream()
                .limit(20)
                .forEach(file -> builder.append("- `")
                        .append(file.fileName()).append("`")
                        .append(file.enabled() ? "" : " [disabled]")
                        .append(" created=").append(DateTimeFormatter.ISO_INSTANT.format(file.createdAt()))
                        .append(" modified=").append(DateTimeFormatter.ISO_INSTANT.format(file.modifiedAt()))
                        .append(" size=").append(file.sizeBytes()).append(" bytes\n"));
        builder.append('\n');
    }

    private void appendProvenance(StringBuilder builder, ReportEnvironment environment) {
        if (environment.modProvenance().isEmpty()) {
            return;
        }

        builder.append("## Provenance hints\n\n");
        builder.append("These are local evidence hints. Local created/modified times are not release or build dates. ")
                .append("Hashes and fingerprints identify exact jars when another local tool has already cached them.\n\n");
        environment.modProvenance().stream()
                .limit(30)
                .forEach(file -> {
                    builder.append("- `").append(file.fileName()).append("`");
                    if (!file.modId().isBlank()) {
                        builder.append(" modId=`").append(file.modId()).append('`');
                    }
                    if (!file.version().isBlank()) {
                        builder.append(" version=").append(file.version());
                    }
                    builder.append(" confidence=").append(file.confidence());
                    if (!file.modrinthHash().isBlank()) {
                        builder.append(" modrinthSha1=").append(file.modrinthHash());
                    }
                    if (!file.curseForgeFingerprint().isBlank()) {
                        builder.append(" curseForgeFingerprint=").append(file.curseForgeFingerprint());
                    }
                    builder.append(" localCreated=").append(DateTimeFormatter.ISO_INSTANT.format(file.createdAt()));
                    builder.append('\n');
                });
        builder.append('\n');
    }

    private void appendChangeSuspects(StringBuilder builder, ReportEnvironment environment) {
        List<ModFileSnapshot> batch = newestNonDoctorBatch(environment.modFiles());
        if (batch.size() < 2) {
            return;
        }

        builder.append("## Change suspects\n\n");
        builder.append("The newest enabled non-Pack-Log-Doctor files were added in the same short time window. ")
                .append("If the pack started failing after a recent mod batch, test-disable this batch first:\n\n");
        for (ModFileSnapshot file : batch) {
            builder.append("- `").append(file.fileName()).append("`\n");
        }
        builder.append('\n');
    }

    private List<ModFileSnapshot> newestNonDoctorBatch(List<ModFileSnapshot> files) {
        ModFileSnapshot newest = files.stream()
                .filter(ModFileSnapshot::enabled)
                .filter(file -> !file.fileName().toLowerCase(java.util.Locale.ROOT).contains("packlogdoctor"))
                .findFirst()
                .orElse(null);
        if (newest == null) {
            return List.of();
        }

        long windowSeconds = 120L;
        return files.stream()
                .filter(ModFileSnapshot::enabled)
                .filter(file -> !file.fileName().toLowerCase(java.util.Locale.ROOT).contains("packlogdoctor"))
                .filter(file -> Math.abs(file.createdAt().getEpochSecond() - newest.createdAt().getEpochSecond()) <= windowSeconds)
                .toList();
    }

    private void appendDependencyContext(StringBuilder builder, ReportSnapshot snapshot) {
        Set<String> relevantModIds = relevantModIds(snapshot);
        if (relevantModIds.isEmpty() || snapshot.environment().modDependencies().isEmpty()) {
            return;
        }

        builder.append("## Dependency context\n\n");
        for (String modId : relevantModIds) {
            ModDependencySnapshot mod = snapshot.environment().modDependencies().get(modId);
            if (mod == null) {
                continue;
            }
            builder.append("### `").append(mod.modId()).append("`");
            if (!mod.displayName().isBlank() && !mod.displayName().equals(mod.modId())) {
                builder.append(" - ").append(mod.displayName());
            }
            builder.append("\n\n");
            builder.append("- Version: ").append(mod.version()).append('\n');
            builder.append("- File: `").append(mod.fileName()).append("` (").append(mod.fileType()).append(")\n");
            appendOptional(builder, "Issue tracker", mod.issueTrackerUrl());
            appendOptional(builder, "Project page", mod.displayUrl());
            if (mod.dependencies().isEmpty()) {
                builder.append("- Dependencies: none declared\n\n");
                continue;
            }
            builder.append("- Dependencies:\n");
            for (DependencySnapshot dependency : mod.dependencies()) {
                builder.append("  - `").append(dependency.modId()).append("` ")
                        .append(dependency.type()).append(" ")
                        .append(dependency.versionRange())
                        .append(" [").append(dependency.status()).append("]");
                if (!dependency.installedVersion().isBlank()) {
                    builder.append(" installed=").append(dependency.installedVersion());
                }
                if (!dependency.side().equals("BOTH")) {
                    builder.append(" side=").append(dependency.side());
                }
                builder.append('\n');
            }
            builder.append('\n');
        }
    }

    private Set<String> relevantModIds(ReportSnapshot snapshot) {
        Set<String> modIds = new LinkedHashSet<>();
        for (IssueSnapshot issue : snapshot.issues()) {
            for (String modId : issue.likelyModCounts().keySet()) {
                if (!"unknown".equals(modId)) {
                    modIds.add(modId);
                }
            }
        }
        return modIds;
    }

    private String formatCounts(Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            first = false;
            builder.append('`').append(entry.getKey()).append("`=").append(entry.getValue());
        }
        return builder.toString();
    }

    private void writeSuggestedSuppressions(ReportSnapshot snapshot) {
        try {
            List<com.sanhiruzu.packlogdoctor.runtime.SuppressionConfig.SuppressionRule> suggested = new java.util.ArrayList<>();
            Map<String, Integer> loggerCounts = new LinkedHashMap<>();

            for (IssueSnapshot issue : snapshot.issues()) {
                if (issue.severity() != Severity.WARN) {
                    continue;
                }
                for (String logger : issue.loggerCounts().keySet()) {
                    loggerCounts.put(logger, loggerCounts.getOrDefault(logger, 0) + issue.loggerCounts().get(logger));
                }
            }

            for (Map.Entry<String, Integer> entry : loggerCounts.entrySet()) {
                if (entry.getValue() >= 3) {
                    suggested.add(new com.sanhiruzu.packlogdoctor.runtime.SuppressionConfig.SuppressionRule(
                            entry.getKey(),
                            null,
                            null
                    ));
                }
            }

            if (!suggested.isEmpty()) {
                com.sanhiruzu.packlogdoctor.runtime.SuppressionConfig.writeSuggested(
                        outputDir.resolve("suppression-config.json"),
                        suggested
                );
            }
        } catch (Exception e) {
        }
    }

    private void writeLog4j2Config() {
        try {
            com.sanhiruzu.packlogdoctor.runtime.SuppressionConfig config =
                    com.sanhiruzu.packlogdoctor.runtime.SuppressionConfig.load(outputDir.resolve("suppression-config.json"));
            com.sanhiruzu.packlogdoctor.runtime.Log4j2ConfigGenerator.generate(
                    outputDir.resolve("log4j2-filter.xml"),
                    config
            );
        } catch (Exception e) {
        }
    }
}
