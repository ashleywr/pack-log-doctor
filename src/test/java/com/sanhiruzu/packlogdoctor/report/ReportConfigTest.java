package com.sanhiruzu.packlogdoctor.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void createsDefaultConfigWhenMissing() {
        ReportConfig config = ReportConfig.loadOrCreate(tempDir);

        assertEquals(ReportConfig.defaults(), config);
        assertTrue(Files.exists(tempDir.resolve("config").resolve("packlogdoctor.json")));
    }

    @Test
    void sanitizesInvalidLimits() {
        ReportConfig config = new ReportConfig(-1, -1, -1, true, true).sanitized();

        assertEquals(ReportConfig.defaults().maxGroupedIssues(), config.maxGroupedIssues());
        assertEquals(ReportConfig.defaults().maxExamplesPerIssue(), config.maxExamplesPerIssue());
        assertEquals(ReportConfig.defaults().maxLoudestMods(), config.maxLoudestMods());
    }
}
