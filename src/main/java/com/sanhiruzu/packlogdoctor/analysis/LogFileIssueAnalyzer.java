package com.sanhiruzu.packlogdoctor.analysis;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LogFileIssueAnalyzer {
    private final Set<String> loadedModIds;

    public LogFileIssueAnalyzer(Set<String> loadedModIds) {
        this.loadedModIds = loadedModIds == null ? Set.of() : Set.copyOf(loadedModIds);
    }

    public Result analyze(Path logPath) throws IOException {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(loadedModIds);
        int observations = 0;
        for (LogObservation observation : LogFileParser.parse(logPath)) {
            analyzer.observe(observation);
            observations++;
        }
        return new Result(observations, analyzer.loudestMods(), analyzer.snapshots());
    }

    public record Result(
            int observationCount,
            Map<String, Integer> loudestMods,
            List<IssueSnapshot> issues
    ) {
        public Result {
            loudestMods = loudestMods == null ? Map.of() : Map.copyOf(loudestMods);
            issues = issues == null ? List.of() : List.copyOf(issues);
        }
    }
}
