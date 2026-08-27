package com.sanhiruzu.packlogdoctor.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public record ReportConfig(
        int maxGroupedIssues,
        int maxExamplesPerIssue,
        int maxLoudestMods,
        boolean writeMarkdown,
        boolean writeJson,
        Boolean writeHtml,
        boolean printSummaryToLog,
        Boolean writeStartupReport,
        String modpackName,
        String modpackVersion
) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int DEFAULT_MAX_GROUPED_ISSUES = 50;
    private static final int DEFAULT_MAX_EXAMPLES_PER_ISSUE = 3;
    private static final int DEFAULT_MAX_LOUDEST_MODS = 20;
    private static final int HARD_MAX_GROUPED_ISSUES = 500;
    private static final int HARD_MAX_EXAMPLES_PER_ISSUE = 10;
    private static final int HARD_MAX_LOUDEST_MODS = 100;

    public static ReportConfig defaults() {
        return new ReportConfig(
                DEFAULT_MAX_GROUPED_ISSUES,
                DEFAULT_MAX_EXAMPLES_PER_ISSUE,
                DEFAULT_MAX_LOUDEST_MODS,
                true,
                true,
                true,
                false,
                true,
                "",
                ""
        );
    }

    public ReportConfig(int maxGroupedIssues, int maxExamplesPerIssue, int maxLoudestMods, boolean writeMarkdown, boolean writeJson) {
        this(maxGroupedIssues, maxExamplesPerIssue, maxLoudestMods, writeMarkdown, writeJson, true, false, true, "", "");
    }

    public static ReportConfig loadOrCreate(Path gameDir) {
        Path path = gameDir.resolve("config").resolve("packlogdoctor.json");
        ReportConfig defaults = defaults();
        if (!Files.exists(path)) {
            writeDefaults(path, defaults);
            return defaults;
        }

        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            ReportConfig loaded = GSON.fromJson(json, ReportConfig.class);
            return loaded == null ? defaults : loaded.sanitized();
        } catch (Exception exception) {
            return defaults;
        }
    }

    private static void writeDefaults(Path path, ReportConfig defaults) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(defaults), StandardCharsets.UTF_8);
        } catch (IOException exception) {
        }
    }

    public ReportConfig sanitized() {
        return new ReportConfig(
                clamp(maxGroupedIssues, 1, HARD_MAX_GROUPED_ISSUES, DEFAULT_MAX_GROUPED_ISSUES),
                clamp(maxExamplesPerIssue, 0, HARD_MAX_EXAMPLES_PER_ISSUE, DEFAULT_MAX_EXAMPLES_PER_ISSUE),
                clamp(maxLoudestMods, 1, HARD_MAX_LOUDEST_MODS, DEFAULT_MAX_LOUDEST_MODS),
                writeMarkdown,
                writeJson,
                writeHtml == null ? true : writeHtml,
                false,
                writeStartupReport == null ? true : writeStartupReport,
                clean(modpackName),
                clean(modpackVersion)
        );
    }

    private static int clamp(int value, int min, int max, int fallback) {
        if (value < min) {
            return fallback;
        }
        return Math.min(value, max);
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.strip();
        return cleaned.length() > 80 ? cleaned.substring(0, 80) : cleaned;
    }
}
