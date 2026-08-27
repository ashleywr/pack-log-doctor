package com.sanhiruzu.packlogdoctor.report;

import java.time.Instant;

public record StartupGapSnapshot(
        Instant timestamp,
        long durationMs,
        String before,
        String after
) {
}
