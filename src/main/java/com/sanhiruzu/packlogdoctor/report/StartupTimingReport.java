package com.sanhiruzu.packlogdoctor.report;

import java.time.Instant;
import java.util.List;

public record StartupTimingReport(
        Instant firstSeen,
        Instant lastSeen,
        long observedDurationMs,
        List<StartupTimingSnapshot> timings,
        List<StartupGapSnapshot> gaps
) {
    public static StartupTimingReport empty() {
        return new StartupTimingReport(Instant.EPOCH, Instant.EPOCH, 0L, List.of(), List.of());
    }
}
