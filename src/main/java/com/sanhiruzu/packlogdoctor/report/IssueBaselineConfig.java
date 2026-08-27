package com.sanhiruzu.packlogdoctor.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sanhiruzu.packlogdoctor.analysis.IssueSnapshot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public final class IssueBaselineConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final List<Rule> rules;

    public IssueBaselineConfig(List<Rule> rules) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    public static IssueBaselineConfig load(Path path) {
        if (!Files.isRegularFile(path)) {
            return new IssueBaselineConfig(List.of());
        }
        try {
            ConfigFile file = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), ConfigFile.class);
            return new IssueBaselineConfig(file == null ? List.of() : file.rules);
        } catch (IOException | com.google.gson.JsonSyntaxException exception) {
            return new IssueBaselineConfig(List.of());
        }
    }

    public IssueDisposition dispositionFor(IssueSnapshot issue) {
        for (Rule rule : rules) {
            if (rule.matches(issue)) {
                return new IssueDisposition(rule.status, rule.note);
            }
        }
        return new IssueDisposition("unexpected", "");
    }

    public List<Rule> rules() {
        return rules;
    }

    public static final class Rule {
        public String status;
        public String signature;
        public String loggerPattern;
        public String messageContains;
        public String note;

        public Rule() {
        }

        public Rule(String status, String signature, String loggerPattern, String messageContains, String note) {
            this.status = status;
            this.signature = signature;
            this.loggerPattern = loggerPattern;
            this.messageContains = messageContains;
            this.note = note;
        }

        private boolean matches(IssueSnapshot issue) {
            if (signature != null && !signature.isBlank() && !signature.equals(issue.signature())) {
                return false;
            }
            if (loggerPattern != null && !loggerPattern.isBlank()
                    && issue.loggerCounts().keySet().stream().noneMatch(logger -> wildcardMatches(loggerPattern, logger))) {
                return false;
            }
            if (messageContains != null && !messageContains.isBlank()
                    && issue.examples().stream().noneMatch(example -> example.contains(messageContains))) {
                return false;
            }
            return true;
        }

        private static boolean wildcardMatches(String pattern, String value) {
            if (!pattern.contains("*")) {
                return pattern.equals(value);
            }
            String regex = Pattern.quote(pattern).replace("*", "\\E.*\\Q");
            return Pattern.compile("^" + regex + "$").matcher(value).matches();
        }
    }

    private static final class ConfigFile {
        List<Rule> rules;
    }
}
