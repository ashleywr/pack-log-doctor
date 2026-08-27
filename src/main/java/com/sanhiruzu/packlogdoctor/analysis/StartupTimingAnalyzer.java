package com.sanhiruzu.packlogdoctor.analysis;

import com.sanhiruzu.packlogdoctor.report.StartupGapSnapshot;
import com.sanhiruzu.packlogdoctor.report.StartupTimingReport;
import com.sanhiruzu.packlogdoctor.report.StartupTimingSnapshot;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StartupTimingAnalyzer {
    private static final Pattern MODERNFIX_SECONDS = Pattern.compile(
            "(?i)\\b(game took|initial datapack load took|time from main menu to in-game was|total time to load game and open world was)\\s+([0-9.]+)\\s*(seconds|second|s)\\b"
    );
    private static final Pattern GENERIC_DURATION = Pattern.compile(
            "(?i)(.{0,120}?\\b(?:took|in|after)\\s+)([0-9.]+)\\s*(milliseconds|millisecond|ms|seconds|second|s)\\b"
    );
    private static final long MIN_EXPLICIT_DURATION_MS = 250L;
    private static final long MIN_GAP_MS = 2_000L;
    private static final int MAX_STORED = 80;

    private final List<StartupTimingSnapshot> timings = new ArrayList<>();
    private final List<StartupGapSnapshot> gaps = new ArrayList<>();
    private Instant firstSeen;
    private Instant lastSeen;
    private Instant previousTimestamp;
    private String previousSummary = "";

    public synchronized void observe(Instant timestamp, String level, String loggerName, String threadName, String message) {
        if (timestamp == null) {
            return;
        }
        String cleanLogger = clean(loggerName);
        String cleanThread = clean(threadName);
        String cleanMessage = clean(message);
        if (cleanMessage.isBlank()) {
            return;
        }
        if (firstSeen == null) {
            firstSeen = timestamp;
        }
        lastSeen = timestamp;

        if (previousTimestamp != null) {
            long gapMs = Duration.between(previousTimestamp, timestamp).toMillis();
            if (gapMs >= MIN_GAP_MS) {
                gaps.add(new StartupGapSnapshot(timestamp, gapMs, previousSummary, summarize(cleanLogger, cleanMessage)));
                trim(gaps);
            }
        }
        previousTimestamp = timestamp;
        previousSummary = summarize(cleanLogger, cleanMessage);

        StartupTimingSnapshot timing = explicitTiming(timestamp, cleanLogger, cleanThread, cleanMessage);
        if (timing != null) {
            timings.add(timing);
            trim(timings);
        }
    }

    public synchronized StartupTimingReport snapshot() {
        Instant start = firstSeen == null ? Instant.EPOCH : firstSeen;
        Instant end = lastSeen == null ? Instant.EPOCH : lastSeen;
        long observedMs = firstSeen == null || lastSeen == null ? 0L : Math.max(0L, Duration.between(start, end).toMillis());
        return new StartupTimingReport(
                start,
                end,
                observedMs,
                timings.stream()
                        .sorted(Comparator.comparingLong(StartupTimingSnapshot::durationMs).reversed())
                        .limit(20)
                        .toList(),
                gaps.stream()
                        .sorted(Comparator.comparingLong(StartupGapSnapshot::durationMs).reversed())
                        .limit(20)
                        .toList()
        );
    }

    private StartupTimingSnapshot explicitTiming(Instant timestamp, String loggerName, String threadName, String message) {
        Matcher modernFix = MODERNFIX_SECONDS.matcher(message);
        if (modernFix.find()) {
            String label = title(modernFix.group(1));
            return new StartupTimingSnapshot(timestamp, category(loggerName, message), label, loggerName, threadName,
                    toMillis(modernFix.group(2), modernFix.group(3)), abbreviate(message, 240));
        }

        Matcher generic = GENERIC_DURATION.matcher(message);
        if (!generic.find()) {
            return null;
        }
        long durationMs = toMillis(generic.group(2), generic.group(3));
        if (durationMs < MIN_EXPLICIT_DURATION_MS) {
            return null;
        }
        String label = abbreviate(generic.group(1).replaceAll("(?i)\\b(took|in|after)\\s+$", "").strip(), 90);
        if (label.isBlank()) {
            label = abbreviate(message, 90);
        }
        return new StartupTimingSnapshot(timestamp, category(loggerName, message), label, loggerName, threadName, durationMs, abbreviate(message, 240));
    }

    private long toMillis(String value, String unit) {
        double amount;
        try {
            amount = Double.parseDouble(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
        String lower = unit.toLowerCase(Locale.ROOT);
        return lower.startsWith("s") ? Math.round(amount * 1000.0) : Math.round(amount);
    }

    private String category(String loggerName, String message) {
        String combined = (loggerName + " " + message).toLowerCase(Locale.ROOT);
        if (combined.contains("modernfix")) {
            return "load-summary";
        }
        if (combined.contains("jei")) {
            return "jei";
        }
        if (combined.contains("ami")) {
            return "ami";
        }
        if (combined.contains("kubejs")) {
            return "kubejs";
        }
        if (combined.contains("cobblemon")) {
            return "cobblemon";
        }
        if (combined.contains("recipe") || combined.contains("datapack") || combined.contains("resource reload")) {
            return "datapack";
        }
        if (combined.contains("shader") || combined.contains("texture") || combined.contains("model")) {
            return "client-assets";
        }
        return "other";
    }

    private String summarize(String loggerName, String message) {
        String logger = loggerName.isBlank() ? "unknown" : loggerName;
        return abbreviate(logger + ": " + message, 220);
    }

    private String title(String text) {
        if (text == null || text.isBlank()) {
            return "Startup timing";
        }
        String[] words = text.toLowerCase(Locale.ROOT).split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String abbreviate(String value, int maxLength) {
        String clean = clean(value).replaceAll("\\s+", " ");
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength - 3) + "...";
    }

    private <T> void trim(List<T> values) {
        if (values.size() > MAX_STORED) {
            values.subList(0, values.size() - MAX_STORED).clear();
        }
    }
}
