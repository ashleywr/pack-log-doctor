package com.sanhiruzu.packlogdoctor.analysis;

import java.time.Instant;
import java.util.Objects;

public record LogObservation(
        Instant timestamp,
        Severity severity,
        String loggerName,
        String threadName,
        String message,
        String throwableSummary
) {
    public LogObservation {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        severity = Objects.requireNonNull(severity, "severity");
        loggerName = sanitize(loggerName);
        threadName = sanitize(threadName);
        message = sanitize(message);
        throwableSummary = throwableSummary == null ? "" : throwableSummary.stripTrailing();
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.strip();
    }
}
