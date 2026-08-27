package com.sanhiruzu.packlogdoctor.report;

import java.time.Instant;
import java.util.Objects;

public record UncaughtExceptionSnapshot(
        Instant timestamp,
        String threadName,
        String message,
        String stackTrace
) {
    public UncaughtExceptionSnapshot {
        timestamp = Objects.requireNonNull(timestamp, "timestamp");
        threadName = sanitize(threadName);
        message = sanitize(message);
        stackTrace = stackTrace == null ? "" : stackTrace.stripTrailing();
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.strip();
    }
}
