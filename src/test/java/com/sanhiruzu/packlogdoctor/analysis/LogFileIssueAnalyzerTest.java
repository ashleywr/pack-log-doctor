package com.sanhiruzu.packlogdoctor.analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogFileIssueAnalyzerTest {
    @TempDir
    Path tempDir;

    @Test
    void analyzesExistingLatestLogIntoGroupedIssues() throws Exception {
        Path log = tempDir.resolve("latest.log");
        Files.writeString(log,
                "[19Jun2026 11:30:13.658] [Render thread/ERROR] [net.minecraft.world.item.crafting.RecipeManager/]: Error parsing recipe letsdocompat:farm_and_charm/cooking/peach_tapioca: Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: youkaisfeasts:peach_tapioca\n"
                        + "[19Jun2026 11:30:14.142] [Render thread/ERROR] [net.minecraft.world.item.crafting.RecipeManager/]: Parsing error loading recipe letsdocompat:farm_and_charm/cooking/peach_tapioca: com.google.gson.JsonParseException: Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: youkaisfeasts:peach_tapioca\n"
                        + "[19Jun2026 11:30:15.229] [Render thread/ERROR] [net.neoforged.neoforge.registries.DataMapLoader/]: Object with ID ecologics:walnut_seedling specified in data map for registry minecraft:item doesn't exist\n");

        LogFileIssueAnalyzer analyzer = new LogFileIssueAnalyzer(Set.of("letsdocompat", "youkaisfeasts", "ecologics"));
        var report = analyzer.analyze(log);

        assertEquals(3, report.observationCount());
        assertEquals(3, report.issues().stream().mapToInt(IssueSnapshot::count).sum());
        assertTrue(report.loudestMods().containsKey("youkaisfeasts"));
        assertTrue(report.loudestMods().containsKey("ecologics"));
    }
}
