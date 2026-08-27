package com.sanhiruzu.packlogdoctor.report;

import com.sanhiruzu.packlogdoctor.analysis.IssueSnapshot;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ReportSnapshot(
        Instant generatedAt,
        String trigger,
        ReportEnvironment environment,
        Set<String> loadedModIds,
        Map<String, Integer> loudestMods,
        StartupTimingReport startupTimings,
        List<UncaughtExceptionSnapshot> uncaughtExceptions,
        List<IssueSnapshot> issues
) {
    public ReportSnapshot(
            Instant generatedAt,
            String trigger,
            Set<String> loadedModIds,
            Map<String, Integer> loudestMods,
            List<IssueSnapshot> issues
    ) {
        this(generatedAt, trigger, ReportEnvironment.unknown(), loadedModIds, loudestMods, StartupTimingReport.empty(), List.of(), issues);
    }

    public ReportSnapshot(
            Instant generatedAt,
            String trigger,
            ReportEnvironment environment,
            Set<String> loadedModIds,
            Map<String, Integer> loudestMods,
            StartupTimingReport startupTimings,
            List<IssueSnapshot> issues
    ) {
        this(generatedAt, trigger, environment, loadedModIds, loudestMods, startupTimings, List.of(), issues);
    }

    public ReportSnapshot {
        if (startupTimings == null) {
            startupTimings = StartupTimingReport.empty();
        }
        if (uncaughtExceptions == null) {
            uncaughtExceptions = List.of();
        }
    }
}
