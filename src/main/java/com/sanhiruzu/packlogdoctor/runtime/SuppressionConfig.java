package com.sanhiruzu.packlogdoctor.runtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Manages suppression rules that filter out known/expected errors from log analysis.
 *
 * Configuration format (suppression-config.json):
 * {
 *   "suppressedPatterns": [
 *     { "loggerPattern": "mixin" },
 *     { "loggerPattern": "net.caffeinemc.*" },
 *     { "message": "Error loading class" },
 *     { "loggerPattern": "some.mod", "message": "specific error text" }
 *   ]
 * }
 *
 * Logger patterns support wildcards (* matches any characters).
 * Messages use simple substring matching (not regex).
 *
 * PackLogDoctor generates log4j2-filter.xml from the suppression config,
 * which can be used as a JVM parameter: -Dlog4j.configurationFile=path/to/log4j2-filter.xml
 */
public final class SuppressionConfig {
    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>)
                    (value, type, context) -> new JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(value)))
            .setPrettyPrinting()
            .create();

    private final List<SuppressionRule> suppressedPatterns;

    public SuppressionConfig(List<SuppressionRule> suppressedPatterns) {
        this.suppressedPatterns = suppressedPatterns == null ? List.of() : List.copyOf(suppressedPatterns);
    }

    public static SuppressionConfig load(Path path) {
        if (!Files.isRegularFile(path)) {
            return new SuppressionConfig(List.of());
        }
        try {
            ConfigFile file = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ConfigFile.class);
            return new SuppressionConfig(file.suppressedPatterns == null ? List.of() : file.suppressedPatterns);
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            return new SuppressionConfig(List.of());
        }
    }

    public static void writeSuggested(Path path, List<SuppressionRule> suggested) throws IOException {
        ConfigFile file = new ConfigFile();
        file.suppressedPatterns = new ArrayList<>(suggested);
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(file), StandardCharsets.UTF_8);
    }

    public boolean isSuppressed(String loggerName, String message) {
        for (SuppressionRule rule : suppressedPatterns) {
            if (rule.matches(loggerName, message)) {
                return true;
            }
        }
        return false;
    }

    public List<SuppressionRule> rules() {
        return suppressedPatterns;
    }

    public static final class SuppressionRule {
        public String loggerPattern;
        public String message;
        public String minSeverity;

        public SuppressionRule() {}

        public SuppressionRule(String loggerPattern, String message, String minSeverity) {
            this.loggerPattern = loggerPattern;
            this.message = message;
            this.minSeverity = minSeverity;
        }

        public boolean matches(String loggerName, String messageText) {
            if (loggerPattern != null && !loggerPattern.isBlank()) {
                if (!patternMatches(loggerPattern, loggerName)) {
                    return false;
                }
            }
            if (message != null && !message.isBlank()) {
                if (!messageText.contains(message)) {
                    return false;
                }
            }
            return true;
        }

        private static boolean patternMatches(String pattern, String input) {
            if (pattern.contains("*")) {
                String regex = pattern.replace(".", "\\.")
                        .replace("*", ".*");
                try {
                    return Pattern.compile("^" + regex + "$").matcher(input).matches();
                } catch (Exception e) {
                    return pattern.equals(input);
                }
            }
            return pattern.equals(input);
        }
    }

    private static final class ConfigFile {
        List<SuppressionRule> suppressedPatterns;
    }
}
