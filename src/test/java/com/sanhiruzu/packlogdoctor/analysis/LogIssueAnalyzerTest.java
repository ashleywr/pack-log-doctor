package com.sanhiruzu.packlogdoctor.analysis;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogIssueAnalyzerTest {
    @Test
    void groupsRepeatedMessagesWithChangingNumbers() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("examplemod"));

        analyzer.observe(observation("Unable to load model examplemod:block/widget at line 12"));
        analyzer.observe(observation("Unable to load model examplemod:block/widget at line 42"));

        var issues = analyzer.snapshots();
        assertEquals(1, issues.size());
        assertEquals(2, issues.getFirst().count());
        assertEquals(IssueCategory.MISSING_RESOURCE, issues.getFirst().category());
        assertEquals(3 * 2, issues.getFirst().likelyModCounts().get("examplemod"));
    }

    @Test
    void classifiesKubeJsAndSuggestsDraftOnlyFix() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("kubejs"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "dev.latvian.mods.kubejs",
                "Render thread",
                "Error while parsing KubeJS server script startup_scripts/foo.js",
                ""
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(IssueCategory.KUBEJS, issue.category());
        assertFalse(issue.suggestions().isEmpty());
        assertEquals("draft-file", issue.suggestions().getFirst().safety());
    }

    @Test
    void reportsUnknownWhenNoLoadedModMatches() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("minecraft"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.WARN,
                "pack.test",
                "Render thread",
                "Something bad happened inside mysterymod:item",
                ""
        ));

        assertEquals("unknown", analyzer.snapshots().getFirst().likelyModCounts().keySet().iterator().next());
    }

    @Test
    void extractsMixinEvidenceFromCommonCrashText() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("armorstandposes"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "mixin",
                "Render thread",
                "Mixin transformation of net.minecraft.world.entity.decoration.ArmorStand failed",
                "Attach error for poses.mixins.json:ArmorStandEntityMixin from mod armorstandposes during activity"
        ));

        var evidence = analyzer.snapshots().getFirst().evidence();
        assertEquals(IssueCategory.MIXIN, analyzer.snapshots().getFirst().category());
        assertEquals("Mixin target: net.minecraft.world.entity.decoration.ArmorStand", evidence.get(0));
        assertEquals("Mixin config: poses.mixins.json:ArmorStandEntityMixin", evidence.get(1));
        assertEquals("Mixin owner: armorstandposes", evidence.get(2));
    }

    @Test
    void attributesModLoadingFailureToExplicitModId() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("spawn_notification", "cobblemon", "tim_core"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "thedarkcolour.kotlinforforge.neoforge.LoggerKt",
                "modloading-worker-0",
                "Failed to create mod instance. ModID: spawn_notification, class us.timinc.mc.cobblemon.spawnnotification.neoforge.SpawnNotificationNeoForge",
                "at TRANSFORMER/spawn_notification@1.7.3-neoforge-2.3.0/us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.<init>(SpawnNotification.kt:41)\n"
                        + "at TRANSFORMER/cobblemon@1.7.3+1.21.1/com.cobblemon.mod.common.api.reactive.SimpleObservable.subscribe(SimpleObservable.java:24)"
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(IssueCategory.MOD_LOADING, issue.category());
        assertEquals("spawn_notification", issue.likelyModCounts().keySet().iterator().next());
    }

    @Test
    void groupsMissingBlockstateModelVariantsWithEvidence() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("create_cobblemon_potion"));

        analyzer.observe(observation("Exception loading blockstate definition: 'create_cobblemon_potion:blockstates/full_heal_block.json' missing model for variant: 'create_cobblemon_potion:full_heal_block#level=7'"));
        analyzer.observe(observation("Exception loading blockstate definition: 'create_cobblemon_potion:blockstates/full_heal_block.json' missing model for variant: 'create_cobblemon_potion:full_heal_block#level=8'"));
        analyzer.observe(observation("Exception loading blockstate definition: 'create_cobblemon_potion:blockstates/full_heal_block.json' missing model for variant: 'create_cobblemon_potion:full_heal_block#level=13'"));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(1, analyzer.snapshots().size());
        assertEquals(3, issue.count());
        assertEquals(IssueCategory.MISSING_RESOURCE, issue.category());
        assertEquals("create_cobblemon_potion", issue.likelyModCounts().keySet().iterator().next());
        assertEquals("Blockstate file: create_cobblemon_potion:blockstates/full_heal_block.json", issue.evidence().get(0));
        assertEquals("Missing model variant: create_cobblemon_potion:full_heal_block#level=7", issue.evidence().get(1));
        assertEquals("Missing model variant: create_cobblemon_potion:full_heal_block#level=8", issue.evidence().get(2));
        assertEquals("log-inferred", issue.suggestions().getFirst().safety());
        assertEquals("Blockstate variant/model mismatch in create_cobblemon_potion", issue.suggestions().getFirst().title());
    }

    @Test
    void suggestsNarrowLoggerSuppressionCandidateForHighVolumeRepeatNoise() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("create_cobblemon_potion"));

        for (int index = 0; index < 20; index++) {
            analyzer.observe(observation("Exception loading blockstate definition: 'create_cobblemon_potion:blockstates/full_heal_block.json' missing model for variant: 'create_cobblemon_potion:full_heal_block#level=" + index + "'"));
        }

        var suggestions = analyzer.snapshots().getFirst().suggestions();
        var suppression = suggestions.stream()
                .filter(suggestion -> suggestion.safety().equals("logging-draft"))
                .findFirst()
                .orElseThrow();
        assertEquals("Suppress only this repeated logger/message after review", suppression.title());
        assertTrue(suppression.actionBody().contains("Logger: net.minecraft.client.resources"));
        assertTrue(suppression.actionBody().contains("Count in report: 20"));
    }

    @Test
    void classifiesModernFixWrongThreadResourceReloadWarning() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("cobblemon", "modernfix"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "ModernFix",
                "Worker-ResourceReload-21",
                "A mod is calling registerReloadListener at the wrong time. This will cause random concurrency crashes when ModernFix is not installed.",
                "java.lang.Exception: registerReloadListener called on wrong thread\n"
                        + "at TRANSFORMER/cobblemon@1.7.3+1.21.1/com.cobblemon.mod.neoforge.client.CobblemonNeoForgeClient.registerResourceReloader$neoforge(CobblemonNeoForgeClient.kt:241)"
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(IssueCategory.RESOURCE_RELOAD, issue.category());
        assertEquals("cobblemon", issue.likelyModCounts().keySet().iterator().next());
        assertEquals("ModernFix diagnostic: registerReloadListener called during resource reload", issue.evidence().get(0));
        assertEquals("Resource reload owner: cobblemon", issue.evidence().get(1));
    }

    @Test
    void groupsRecipeErrorsByMissingRegistryKeyAndKeepsRecipeEvidence() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("silentgear"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "net.minecraft.world.item.crafting.RecipeManager",
                "Render thread",
                "Parsing error loading recipe silentgear:woodcutting/stripped_netherwood_log: com.google.gson.JsonParseException: Unknown registry key in ResourceKey[minecraft:root / minecraft:recipe_serializer]: woodcutter:woodcutting",
                ""
        ));
        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:01Z"),
                Severity.ERROR,
                "net.minecraft.world.item.crafting.RecipeManager",
                "Render thread",
                "Parsing error loading recipe silentgear:woodcutting/netherwood_slab: com.google.gson.JsonParseException: Unknown registry key in ResourceKey[minecraft:root / minecraft:recipe_serializer]: woodcutter:woodcutting",
                ""
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(1, analyzer.snapshots().size());
        assertEquals(2, issue.count());
        assertEquals(IssueCategory.RECIPE, issue.category());
        assertEquals("Recipe id: silentgear:woodcutting/stripped_netherwood_log", issue.evidence().get(0));
        assertEquals("Missing registry key: woodcutter:woodcutting", issue.evidence().get(1));
        assertEquals("Recipe id: silentgear:woodcutting/netherwood_slab", issue.evidence().get(2));

        var suggestion = issue.suggestions().getFirst();
        assertEquals("kubejs-draft", suggestion.safety());
        assertEquals("Remove recipes that reference missing optional integration keys", suggestion.title());
        assertTrue(suggestion.actionBody().contains("event.remove({ id: 'silentgear:woodcutting/stripped_netherwood_log' });"));
        assertTrue(suggestion.actionBody().contains("event.remove({ id: 'silentgear:woodcutting/netherwood_slab' });"));
        assertTrue(suggestion.actionBody().contains("Missing keys: woodcutter:woodcutting"));
    }

    @Test
    void classifiesInvalidPackPathAsResourcePackIssue() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("cobblemon"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "net.minecraft.Util",
                "Render thread",
                "Invalid path in pack: cobblemon:textures/particle/generic/impact/LICENSE, ignoring",
                ""
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(IssueCategory.RESOURCE_PACK, issue.category());
        assertEquals("Invalid pack path: cobblemon:textures/particle/generic/impact/LICENSE", issue.evidence().getFirst());
        assertEquals("Report packaged resource path issue", issue.suggestions().getFirst().title());
    }

    @Test
    void groupsRepeatedInvalidLicensePackPathsByNamespaceAndFileName() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("cobblemon"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "net.minecraft.Util",
                "Render thread",
                "Invalid path in pack: cobblemon:textures/particle/generic/impact/LICENSE, ignoring",
                ""
        ));
        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:01Z"),
                Severity.ERROR,
                "net.minecraft.Util",
                "Render thread",
                "Invalid path in pack: cobblemon:textures/particle/balls/timerball/LICENSE, ignoring",
                ""
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(1, analyzer.snapshots().size());
        assertEquals(2, issue.count());
        assertEquals(IssueCategory.RESOURCE_PACK, issue.category());
        assertEquals("Invalid pack path: cobblemon:textures/particle/generic/impact/LICENSE", issue.evidence().get(0));
        assertEquals("Invalid pack path: cobblemon:textures/particle/balls/timerball/LICENSE", issue.evidence().get(1));
    }

    @Test
    void groupsLootTableRegistryErrorsByOwningNamespaceAndMissingNamespace() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("createcasing"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "net.minecraft.world.level.storage.loot.LootDataType",
                "Render thread",
                "Couldn't parse element ResourceKey[minecraft:root / minecraft:loot_table]:createcasing:blocks/refined_radiance_slicer - Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: createcasing:refined_radiance_slicer",
                ""
        ));
        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:01Z"),
                Severity.ERROR,
                "net.minecraft.world.level.storage.loot.LootDataType",
                "Render thread",
                "Couldn't parse element ResourceKey[minecraft:root / minecraft:loot_table]:createcasing:blocks/copper_slicer - Unknown registry key in ResourceKey[minecraft:root / minecraft:item]: createcasing:copper_slicer",
                ""
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(1, analyzer.snapshots().size());
        assertEquals(2, issue.count());
        assertEquals(IssueCategory.REGISTRY, issue.category());
        assertEquals("Registry element: createcasing:blocks/refined_radiance_slicer", issue.evidence().get(0));
        assertEquals("Missing registry key: createcasing:refined_radiance_slicer", issue.evidence().get(1));
    }

    @Test
    void classifiesVeilShaderAndResourceLoadErrorsAsMissingResources() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("veil", "mekanism"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "Veil",
                "Render thread",
                "Couldn't load vanilla shader from mekanism:shaders/core/rendertype_mekasuit.fsh, skipping",
                ""
        ));
        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:01Z"),
                Severity.ERROR,
                "Veil",
                "Render thread",
                "Error loading resource: root:pack.mcmeta",
                "at TRANSFORMER/veil@4.0.0/foundry.veil.mixin.SomeMixin"
        ));

        var issues = analyzer.snapshots();
        assertEquals(IssueCategory.MISSING_RESOURCE, issues.get(0).category());
        assertEquals(IssueCategory.MISSING_RESOURCE, issues.get(1).category());
    }

    @Test
    void suggestsKnownSpawnNotificationVersionAlignmentIssue() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("spawn_notification", "cobblemon", "tim_core"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "thedarkcolour.kotlinforforge.neoforge.LoggerKt",
                "modloading-worker-0",
                "Failed to create mod instance. ModID: spawn_notification",
                "java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0\n"
                        + "at TRANSFORMER/cobblemon@1.7.3+1.21.1/com.cobblemon.mod.common.api.reactive.SimpleObservable.subscribe(SimpleObservable.java:24)\n"
                        + "at TRANSFORMER/tim_core@1.7.3-neoforge-1.32.0/us.timinc.mc.cobblemon.timcore.AbstractMod.<init>(AbstractMod.java:136)\n"
                        + "at TRANSFORMER/spawn_notification@1.7.3-neoforge-2.3.0/us.timinc.mc.cobblemon.spawnnotification.SpawnNotification.<init>(SpawnNotification.kt:41)"
        ));

        var suggestion = analyzer.snapshots().getFirst().suggestions().getFirst();
        assertEquals("known-issue", suggestion.safety());
        assertEquals("Spawn Notification compatibility with Cobblemon 1.7.3", suggestion.title());
    }

    @Test
    void suggestsGtceuKubeJsVersionAlignmentForMissingServerEventsClass() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("gtceu", "kubejs"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "net.minecraft.client.Minecraft",
                "Render thread",
                "Reported exception thrown!",
                "java.lang.NoClassDefFoundError: dev/latvian/mods/kubejs/bindings/event/ServerEvents\n"
                        + "at TRANSFORMER/gtceu@7.0.2/com.gregtechceu.gtceu.data.recipe.GTRecipes$KJSCallWrapper.recipeEventHasListeners(GTRecipes.java:134)\n"
                        + "at TRANSFORMER/gtceu@7.0.2/com.gregtechceu.gtceu.data.recipe.GTRecipes.recipeAddition(GTRecipes.java:112)\n"
                        + "at TRANSFORMER/minecraft@1.21.1/net.minecraft.server.ReloadableServerResources.loadResources(ReloadableServerResources.java)\n"
                        + "Caused by: java.lang.ClassNotFoundException: dev.latvian.mods.kubejs.bindings.event.ServerEvents"
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(IssueCategory.KUBEJS, issue.category());
        assertEquals("gtceu", issue.likelyModCounts().keySet().iterator().next());
        assertEquals("Missing class: dev/latvian/mods/kubejs/bindings/event/ServerEvents", issue.evidence().getFirst());

        var suggestion = issue.suggestions().getFirst();
        assertEquals("version-alignment", suggestion.safety());
        assertEquals("GTCEu is calling a KubeJS class that is not present", suggestion.title());
    }

    @Test
    void suggestsKnownCreateDecoPlacardRecipeWorkaround() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("createdeco", "kubejs"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "net.minecraft.world.item.crafting.RecipeManager",
                "Render thread",
                "Parsing error loading recipe createdeco:placard: com.google.gson.JsonParseException: Failed to parse either. First: Not a json array: {\"id\":\"minecraft:white_dye\"}; Second: No key tag in MapLike[{\"id\":\"minecraft:white_dye\"}]; No key item in MapLike[{\"id\":\"minecraft:white_dye\"}]",
                ""
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(IssueCategory.RECIPE, issue.category());
        assertEquals("createdeco", issue.likelyModCounts().keySet().iterator().next());
        assertEquals("Recipe id: createdeco:placard", issue.evidence().getFirst());

        var suggestion = issue.suggestions().getFirst();
        assertEquals("known-issue", suggestion.safety());
        assertEquals("Create Deco placard recipe fails on NeoForge 1.21.1", suggestion.title());
        assertEquals("https://github.com/talrey/CreateDeco/issues/268", suggestion.url());
        assertFalse(suggestion.actionBody().isBlank());
    }

    @Test
    void extractsNeoForgeMandatoryDependencyConflictBeforeKubeJsClassification() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("taczjs"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "net.neoforged.fml.loading.ModSorter",
                "main",
                "Missing or unsupported mandatory dependencies:\n"
                        + "\tMod ID: 'kubejs', Requested by: 'taczjs', Expected range: '[2101.7.2,)', Actual version: '[MISSING]'",
                ""
        ));
        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:01Z"),
                Severity.FATAL,
                "net.neoforged.fml.ModLoader",
                "Render thread",
                "Error during pre-loading phase: Mod taczjs requires kubejs 2101.7.2 or above",
                ""
        ));

        var issues = analyzer.snapshots();
        assertEquals(IssueCategory.MISSING_DEPENDENCY, issues.get(0).category());
        var dependencyBlock = issues.stream()
                .filter(issue -> issue.evidence().contains("Dependency: kubejs"))
                .findFirst()
                .orElseThrow();
        assertEquals("taczjs", dependencyBlock.likelyModCounts().keySet().iterator().next());
        assertEquals("Dependency: kubejs", dependencyBlock.evidence().get(0));
        assertEquals("Requested by: taczjs", dependencyBlock.evidence().get(1));
        assertEquals("Expected range: [2101.7.2,)", dependencyBlock.evidence().get(2));
        assertEquals("Actual version: [MISSING]", dependencyBlock.evidence().get(3));
        assertEquals("Install, re-enable, update, or remove the requesting mod", dependencyBlock.suggestions().getFirst().title());
    }

    @Test
    void classifiesLongJeiRegistrationAsPerformanceIssue() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("jei"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.ERROR,
                "mezz.jei.library.load.PluginCallerTimerRunnable",
                "pool-54-thread-1",
                "Registering ingredients: jei:minecraft is running and has taken 40.10 seconds so far",
                ""
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(IssueCategory.PERFORMANCE, issue.category());
        assertEquals("Long JEI task: Registering ingredients for jei:minecraft took at least 40.10 seconds", issue.evidence().getFirst());
    }

    @Test
    void classifiesGuidebookMissingRecipesAsFeatureDegradation() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("patchouli", "cobblemon"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.WARN,
                "patchouli",
                "Render thread",
                "Recipe cobblemon:carbos (of type minecraft:crafting) not found",
                ""
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(IssueCategory.FEATURE_DEGRADATION, issue.category());
        assertEquals("Guidebook missing recipe: cobblemon:carbos", issue.evidence().getFirst());
    }

    @Test
    void classifiesModonomiconMissingRecipesAsFeatureDegradationEvidence() {
        LogIssueAnalyzer analyzer = new LogIssueAnalyzer(Set.of("modonomicon", "spectrum"));

        analyzer.observe(new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.WARN,
                "modonomicon",
                "Render thread",
                "Recipe spectrum:pedestal/tier1 (of type minecraft:crafting) not found",
                ""
        ));

        var issue = analyzer.snapshots().getFirst();
        assertEquals(IssueCategory.FEATURE_DEGRADATION, issue.category());
        assertEquals("Guidebook missing recipe: spectrum:pedestal/tier1", issue.evidence().getFirst());
    }

    private LogObservation observation(String message) {
        return new LogObservation(
                Instant.parse("2026-06-06T00:00:00Z"),
                Severity.WARN,
                "net.minecraft.client.resources",
                "Render thread",
                message,
                ""
        );
    }
}
