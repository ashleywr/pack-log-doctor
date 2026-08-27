package com.sanhiruzu.packlogdoctor.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LogFileParserTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesWarnErrorFatalEventsAndKeepsContinuationLines() throws Exception {
        Path log = tempDir.resolve("latest.log");
        Files.writeString(log,
                "[19Jun2026 11:28:40.510] [modloading-worker-0/WARN] [Puzzles Lib/]: Failed to generate mod list\n"
                        + "java.lang.NullPointerException: broken\n"
                        + "\tat example.Mod.method(Mod.java:12)\n"
                        + "[19Jun2026 11:28:56.249] [Worker-ResourceReload-5/ERROR] [GeckoLib/]: Unable to parse animation: shot\n"
                        + "\t-> Failed to parse expression '0+'\n"
                        + "[19Jun2026 11:29:00.000] [Render thread/INFO] [minecraft/]: Ignored info line\n"
                        + "[19Jun2026 11:29:01.000] [Render thread/FATAL] [example/]: Fatal issue\n");

        var observations = LogFileParser.parse(log);

        assertEquals(3, observations.size());
        assertEquals(Severity.WARN, observations.get(0).severity());
        assertEquals("Puzzles Lib", observations.get(0).loggerName());
        assertEquals("modloading-worker-0", observations.get(0).threadName());
        assertEquals("Failed to generate mod list", observations.get(0).message());
        assertEquals("java.lang.NullPointerException: broken\n\tat example.Mod.method(Mod.java:12)",
                observations.get(0).throwableSummary());
        assertEquals(Severity.ERROR, observations.get(1).severity());
        assertEquals("\t-> Failed to parse expression '0+'", observations.get(1).throwableSummary());
        assertEquals(Severity.FATAL, observations.get(2).severity());
    }
}
