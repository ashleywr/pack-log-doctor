package com.sanhiruzu.packlogdoctor.report;

import java.time.Instant;

public record StartupTimingSnapshot(
        Instant timestamp,
        String category,
        String label,
        String loggerName,
        String threadName,
        long durationMs,
        String detail
) {
}
