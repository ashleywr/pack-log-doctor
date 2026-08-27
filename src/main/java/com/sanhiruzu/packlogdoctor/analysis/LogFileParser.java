package com.sanhiruzu.packlogdoctor.analysis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogFileParser {
    private static final Pattern EVENT = Pattern.compile(
            "^\\[([^]]+)] \\[([^]/]+)/([A-Z]+)] \\[([^]]+)](?:\\/(?:))?: (.*)$");
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("ddMMMyyyy HH:mm:ss.SSS", Locale.ENGLISH);

    private LogFileParser() {
    }

    public static List<LogObservation> parse(Path logPath) throws IOException {
        List<LogObservation> observations = new ArrayList<>();
        PendingEvent pending = null;
        for (String line : Files.readAllLines(logPath, StandardCharsets.UTF_8)) {
            Matcher matcher = EVENT.matcher(line);
            if (matcher.matches()) {
                if (pending != null) {
                    pending.addTo(observations);
                }
                Severity severity = severity(matcher.group(3));
                if (severity == null) {
                    pending = null;
                    continue;
                }
                pending = new PendingEvent(
                        timestamp(matcher.group(1)),
                        severity,
                        cleanLogger(matcher.group(4)),
                        matcher.group(2).strip(),
                        matcher.group(5).strip()
                );
            } else if (pending != null) {
                pending.addContinuation(line);
            }
        }
        if (pending != null) {
            pending.addTo(observations);
        }
        return List.copyOf(observations);
    }

    private static Severity severity(String level) {
        return switch (level) {
            case "WARN" -> Severity.WARN;
            case "ERROR" -> Severity.ERROR;
            case "FATAL" -> Severity.FATAL;
            default -> null;
        };
    }

    private static Instant timestamp(String value) {
        return LocalDateTime.parse(value, TIMESTAMP)
                .atZone(ZoneId.systemDefault())
                .toInstant();
    }

    private static String cleanLogger(String value) {
        String logger = value.strip();
        return logger.endsWith("/") ? logger.substring(0, logger.length() - 1) : logger;
    }

    private static final class PendingEvent {
        private final Instant timestamp;
        private final Severity severity;
        private final String loggerName;
        private final String threadName;
        private final String message;
        private final List<String> continuation = new ArrayList<>();

        private PendingEvent(Instant timestamp, Severity severity, String loggerName, String threadName, String message) {
            this.timestamp = timestamp;
            this.severity = severity;
            this.loggerName = loggerName;
            this.threadName = threadName;
            this.message = message;
        }

        private void addContinuation(String line) {
            continuation.add(line);
        }

        private void addTo(List<LogObservation> observations) {
            observations.add(new LogObservation(
                    timestamp,
                    severity,
                    loggerName,
                    threadName,
                    message,
                    String.join("\n", continuation)
            ));
        }
    }
}
