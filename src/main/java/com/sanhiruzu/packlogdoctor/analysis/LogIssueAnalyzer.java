package com.sanhiruzu.packlogdoctor.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LogIssueAnalyzer {
    private static final Pattern RESOURCE_ID = Pattern.compile("\\b([a-z0-9_.-]+):[a-z0-9_./-]+\\b");
    private static final Pattern HEX = Pattern.compile("0x[0-9a-f]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER = Pattern.compile("\\b\\d+\\b");
    private static final Pattern QUOTED_PATH = Pattern.compile("(['\"])(?:[A-Za-z]:)?[^'\"]{8,}\\1");
    private static final Pattern MIXIN_TARGET = Pattern.compile("mixin transformation of ([\\w.$/]+) failed", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIXIN_CONFIG = Pattern.compile("\\b([a-z0-9_.-]+\\.mixins?\\.json(?::[\\w.$]+)?)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MIXIN_FROM_MOD = Pattern.compile("\\bfrom mod ([a-z0-9_.-]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLICIT_MOD_ID = Pattern.compile("\\b(?:modid|mod id)\\s*:\\s*([a-z0-9_.-]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOD_LOADING_ISSUE_FOR = Pattern.compile("\\bmod loading issue for:\\s*([a-z0-9_.-]+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRANSFORMER_MOD = Pattern.compile("\\bTRANSFORMER/([a-z0-9_.-]+)@", Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_CLASS = Pattern.compile(
            "\\b(?:noclassdeffounderror|classnotfoundexception):\\s*([a-z0-9_.$/-]+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern RECIPE_LOAD_ERROR = Pattern.compile(
            "\\bparsing error loading recipe ([a-z0-9_.-]+:[a-z0-9_./-]+):",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNKNOWN_REGISTRY_KEY = Pattern.compile(
            "\\bunknown registry key in ResourceKey\\[[^]]+]:\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOT_REGISTRY_ERROR = Pattern.compile(
            "\\bcouldn't parse element ResourceKey\\[[^]]+]:([a-z0-9_.-]+:[a-z0-9_./-]+)\\s+-\\s+unknown registry key in ResourceKey\\[[^]]+]:\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INVALID_PACK_PATH = Pattern.compile(
            "\\binvalid path in pack:\\s*([a-z0-9_.-]+:[^,]+), ignoring\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_BLOCKSTATE_MODEL = Pattern.compile(
            "blockstate definition:\\s*'([^']+)'\\s+missing model for variant:\\s*'([^']+)'",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_OR_UNSUPPORTED_DEPENDENCY = Pattern.compile(
            "mod id:\\s*'([^']+)',\\s*requested by:\\s*'([^']+)',\\s*expected range:\\s*'([^']+)',\\s*actual version:\\s*'([^']+)'",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRELOAD_DEPENDENCY_ERROR = Pattern.compile(
            "\\bmod\\s+([a-z0-9_.-]+)\\s+requires\\s+([a-z0-9_.-]+)\\s+([^\\n\\r]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JEI_LONG_TASK = Pattern.compile(
            "\\b(registering [^:]+):\\s*([a-z0-9_.-]+:[a-z0-9_.-]+)\\s+is running and has taken\\s+([0-9.]+)\\s+seconds so far\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JEI_EMPTY_GROUP = Pattern.compile(
            "\\bitem group has no display items and no search tab display items\\. items from this group will be missing from the jei ingredient list\\.\\s*(.+)$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PATCHOULI_MISSING_RECIPE = Pattern.compile(
            "\\brecipe\\s+([a-z0-9_.-]+:[a-z0-9_./-]+)\\s+\\(of type ([^)]+)\\) not found\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern MISSING_SOUND = Pattern.compile(
            "\\bmissing sound for event:\\s*([a-z0-9_.-]+:[a-z0-9_./-]+)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SHADER_MISMATCH = Pattern.compile(
            "\\bshader\\s+([a-z0-9_.-]+:[a-z0-9_./-]+|[a-z0-9_./-]+)\\s+could not find\\s+(uniform|sampler)\\s+named\\s+([a-z0-9_]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INVALID_SPRITE_FRAME = Pattern.compile(
            "\\binvalid frame index on sprite\\s+([a-z0-9_.-]+:[a-z0-9_./-]+)\\s+frame\\s+(\\d+):",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern LOOT_TABLE_VALIDATION = Pattern.compile(
            "\\bfound loot table element validation problem in \\{minecraft:loot_table:([a-z0-9_.-]+:[a-z0-9_./-]+)}.*unknown loot table called\\s+([a-z0-9_.-]+:[a-z0-9_./-]+)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEBUG_GUARDIAN_HIGH_MEMORY = Pattern.compile(
            "\\bhigh memory usage detected:\\s*(\\d+)\\s*mb\\s*\\(threshold\\s*(\\d+)\\s*mb\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DEBUG_GUARDIAN_WORLD_HANG = Pattern.compile(
            "\\bworld hang report written to\\s+(.+?)\\s+\\(top suspect:\\s*([^)]*)\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SERVER_UNRESPONSIVE = Pattern.compile(
            "\\bserver thread\\s+([A-Z_]+)\\s+unresponsive for\\s+(\\d+)\\s*ms\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern CANT_KEEP_UP = Pattern.compile(
            "\\bcan't keep up!.*running\\s+(\\d+)ms\\s+or\\s+(\\d+)\\s+ticks behind\\b",
            Pattern.CASE_INSENSITIVE);

    private final Set<String> loadedModIds;
    private final Map<String, MutableIssue> issues = new HashMap<>();

    public LogIssueAnalyzer(Set<String> loadedModIds) {
        this.loadedModIds = new HashSet<>();
        for (String modId : loadedModIds) {
            if (modId != null && !modId.isBlank()) {
                this.loadedModIds.add(modId.toLowerCase(Locale.ROOT));
            }
        }
    }

    public synchronized void observe(LogObservation observation) {
        String signature = signatureFor(observation);
        MutableIssue issue = issues.computeIfAbsent(signature, key -> new MutableIssue(key, classify(observation)));
        issue.add(observation, likelyMods(observation));
    }

    public synchronized List<IssueSnapshot> snapshots() {
        return issues.values().stream()
                .map(MutableIssue::snapshot)
                .sorted(Comparator.comparing(IssueSnapshot::severity).reversed()
                        .thenComparing(IssueSnapshot::count, Comparator.reverseOrder())
                        .thenComparing(IssueSnapshot::lastSeen, Comparator.reverseOrder()))
                .toList();
    }

    public synchronized Map<String, Integer> loudestMods() {
        Map<String, Integer> counts = new HashMap<>();
        for (MutableIssue issue : issues.values()) {
            issue.likelyModCounts.forEach((modId, count) -> counts.merge(modId, count, Integer::sum));
        }
        return sortCounts(counts);
    }

    private String signatureFor(LogObservation observation) {
        String message = observation.message().toLowerCase(Locale.ROOT);
        message = QUOTED_PATH.matcher(message).replaceAll("$1<path>$1");
        message = HEX.matcher(message).replaceAll("<hex>");
        message = NUMBER.matcher(message).replaceAll("<num>");
        message = message.replace('\\', '/').replaceAll("\\s+", " ").strip();
        if (message.contains("parsing error loading recipe")
                && message.contains("unknown registry key")) {
            message = RECIPE_LOAD_ERROR.matcher(message).replaceFirst("parsing error loading recipe <recipe>:");
        }
        Matcher invalidPackPath = INVALID_PACK_PATH.matcher(message);
        if (invalidPackPath.find()) {
            message = normalizeInvalidPackPathSignature(invalidPackPath.group(1));
        }
        Matcher lootRegistryError = LOOT_REGISTRY_ERROR.matcher(message);
        if (lootRegistryError.find()) {
            message = normalizeLootRegistrySignature(lootRegistryError.group(1), lootRegistryError.group(2));
        }
        Matcher dependencyConflict = MISSING_OR_UNSUPPORTED_DEPENDENCY.matcher(message);
        if (dependencyConflict.find()) {
            message = "missing or unsupported dependency " + dependencyConflict.group(1)
                    + " requested by " + dependencyConflict.group(2);
        }
        Matcher jeiLongTask = JEI_LONG_TASK.matcher(message);
        if (jeiLongTask.find()) {
            message = "jei " + jeiLongTask.group(1) + " plugin is running for a long time";
        }
        Matcher patchouliMissingRecipe = PATCHOULI_MISSING_RECIPE.matcher(message);
        if (patchouliMissingRecipe.find()) {
            message = "guidebook references missing recipe from " + namespaceOf(patchouliMissingRecipe.group(1));
        }
        Matcher shaderMismatch = SHADER_MISMATCH.matcher(message);
        if (shaderMismatch.find()) {
            message = "shader missing " + shaderMismatch.group(2) + " " + shaderMismatch.group(3);
        }
        Matcher missingSound = MISSING_SOUND.matcher(message);
        if (missingSound.find()) {
            message = "missing sound event from " + namespaceOf(missingSound.group(1));
        }
        Matcher invalidSpriteFrame = INVALID_SPRITE_FRAME.matcher(message);
        if (invalidSpriteFrame.find()) {
            message = "invalid animated sprite frame from " + namespaceOf(invalidSpriteFrame.group(1));
        }
        Matcher lootTableValidation = LOOT_TABLE_VALIDATION.matcher(message);
        if (lootTableValidation.find()) {
            message = "loot table references missing loot table from " + namespaceOf(lootTableValidation.group(2));
        }
        if (message.contains("high memory usage detected")) {
            message = "high memory usage detected";
        }
        if (message.contains("world hang report written to")) {
            message = "world hang report written";
        }
        if (message.contains("server thread") && message.contains("unresponsive for")) {
            message = "server thread unresponsive";
        }
        if (message.contains("can't keep up!") && message.contains("ticks behind")) {
            message = "server can't keep up";
        }
        if (message.length() > 240) {
            message = message.substring(0, 240);
        }
        return observation.severity() + "|" + observation.loggerName().toLowerCase(Locale.ROOT) + "|" + message;
    }

    private String normalizeInvalidPackPathSignature(String resourcePath) {
        int separator = resourcePath.indexOf(':');
        if (separator < 0 || separator == resourcePath.length() - 1) {
            return "invalid path in pack: <path>, ignoring";
        }
        String namespace = resourcePath.substring(0, separator);
        String path = resourcePath.substring(separator + 1);
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        if (fileName.equals("license") || fileName.equals("readme.md")) {
            return "invalid path in pack: " + namespace + ":**/" + fileName + ", ignoring";
        }
        return "invalid path in pack: " + namespace + ":<path>, ignoring";
    }

    private String normalizeLootRegistrySignature(String elementId, String missingKey) {
        String elementNamespace = namespaceOf(elementId);
        String missingNamespace = namespaceOf(missingKey);
        return "couldn't parse registry element from " + elementNamespace
                + " because registry key from " + missingNamespace + " is missing";
    }

    private static String namespaceOf(String resourceId) {
        int separator = resourceId.indexOf(':');
        if (separator < 0) {
            return resourceId;
        }
        return resourceId.substring(0, separator);
    }

    private IssueCategory classify(LogObservation observation) {
        String haystack = (observation.loggerName() + "\n" + observation.message() + "\n" + observation.throwableSummary())
                .toLowerCase(Locale.ROOT);
        if (haystack.contains("missing or unsupported mandatory dependencies")
                || haystack.contains("missing or unsupported dependency")
                || haystack.contains("requires kubejs")
                || (haystack.contains("missing") && haystack.contains("dependency"))) {
            return IssueCategory.MISSING_DEPENDENCY;
        }
        if (haystack.contains("is running and has taken")
                || haystack.contains("high memory usage detected")
                || haystack.contains("world hang report written")
                || (haystack.contains("server thread") && haystack.contains("unresponsive for"))
                || (haystack.contains("can't keep up!") && haystack.contains("ticks behind"))
                || haystack.contains("world gen stuck")) {
            return IssueCategory.PERFORMANCE;
        }
        if (haystack.contains("items from this group will be missing from the jei ingredient list")
                || haystack.contains("missing sound for event")
                || haystack.contains("could not find uniform named")
                || haystack.contains("could not find sampler named")
                || haystack.contains("invalid frame index on sprite")
                || ((haystack.contains("modonomicon") || haystack.contains("patchouli") || haystack.contains("guidebook"))
                && PATCHOULI_MISSING_RECIPE.matcher(haystack).find())) {
            return IssueCategory.FEATURE_DEGRADATION;
        }
        if (haystack.contains("kubejs")) {
            return IssueCategory.KUBEJS;
        }
        if (haystack.contains("invalid path in pack")) {
            return IssueCategory.RESOURCE_PACK;
        }
        if ((haystack.contains("missing") || haystack.contains("unable to load") || haystack.contains("failed to load")
                || haystack.contains("couldn't load") || haystack.contains("error loading resource"))
                && (haystack.contains("texture") || haystack.contains("model") || haystack.contains("resource")
                || haystack.contains("shader"))) {
            return IssueCategory.MISSING_RESOURCE;
        }
        if (haystack.contains("mixin") || haystack.contains("injection")) {
            return IssueCategory.MIXIN;
        }
        if (haystack.contains("recipe")) {
            return IssueCategory.RECIPE;
        }
        if (haystack.contains("datapack") || haystack.contains("data pack") || haystack.contains("tag")) {
            return IssueCategory.DATAPACK;
        }
        if (haystack.contains("registry")
                || haystack.contains("unknown item")
                || haystack.contains("unknown block")
                || haystack.contains("unknown loot table called")) {
            return IssueCategory.REGISTRY;
        }
        if (haystack.contains("failed to create mod instance")
                || haystack.contains("mod loading has failed")
                || haystack.contains("mod loading issue for:")
                || haystack.contains("has failed to load correctly")) {
            return IssueCategory.MOD_LOADING;
        }
        if (haystack.contains("registerreloadlistener")
                || haystack.contains("resource reload")
                || haystack.contains("resourcereload")) {
            return IssueCategory.RESOURCE_RELOAD;
        }
        if (haystack.contains("missing") && (haystack.contains("dependency") || haystack.contains("mod"))) {
            return IssueCategory.MISSING_DEPENDENCY;
        }
        return IssueCategory.UNKNOWN;
    }

    private Map<String, Integer> likelyMods(LogObservation observation) {
        Map<String, Integer> counts = new HashMap<>();
        String logger = observation.loggerName().toLowerCase(Locale.ROOT);
        String combined = (observation.message() + "\n" + observation.throwableSummary()).toLowerCase(Locale.ROOT);

        Matcher matcher = RESOURCE_ID.matcher(combined);
        while (matcher.find()) {
            addIfLoaded(counts, matcher.group(1), 3);
        }
        addExplicitModMatches(counts, combined, EXPLICIT_MOD_ID, 5);
        addExplicitModMatches(counts, combined, MOD_LOADING_ISSUE_FOR, 5);
        addExplicitModMatches(counts, combined, TRANSFORMER_MOD, 4);
        addExplicitModMatches(counts, combined, MISSING_OR_UNSUPPORTED_DEPENDENCY, 5, 2);
        addExplicitModMatches(counts, combined, PRELOAD_DEPENDENCY_ERROR, 5, 1);

        for (String modId : loadedModIds) {
            if (logger.contains(modId)) {
                counts.merge(modId, 2, Integer::sum);
            }
            if (combined.contains("." + modId + ".") || combined.contains("/" + modId + "/")) {
                counts.merge(modId, 1, Integer::sum);
            }
            if (combined.contains("/" + modId + "@") || combined.contains("modid: " + modId)) {
                counts.merge(modId, 2, Integer::sum);
            }
        }

        if (counts.size() > 1) {
            counts.remove("minecraft");
        }
        if (counts.isEmpty()) {
            counts.put("unknown", 1);
        }
        return counts;
    }

    private void addIfLoaded(Map<String, Integer> counts, String modId, int weight) {
        String normalized = modId.toLowerCase(Locale.ROOT);
        if (loadedModIds.contains(normalized)) {
            counts.merge(normalized, weight, Integer::sum);
        }
    }

    private void addExplicitModMatches(Map<String, Integer> counts, String text, Pattern pattern, int weight) {
        addExplicitModMatches(counts, text, pattern, weight, 1);
    }

    private void addExplicitModMatches(Map<String, Integer> counts, String text, Pattern pattern, int weight, int group) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            addIfLoaded(counts, matcher.group(group), weight);
        }
    }

    private static Map<String, Integer> sortCounts(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), LinkedHashMap::putAll);
    }

    private static final class MutableIssue {
        private final String signature;
        private final IssueCategory category;
        private Severity severity = Severity.WARN;
        private int count;
        private Instant firstSeen;
        private Instant lastSeen;
        private final Map<String, Integer> loggerCounts = new HashMap<>();
        private final Map<String, Integer> likelyModCounts = new HashMap<>();
        private final List<String> examples = new ArrayList<>();
        private final List<FixSuggestion> knownIssueSuggestions = new ArrayList<>();
        private final List<String> evidence = new ArrayList<>();

        private MutableIssue(String signature, IssueCategory category) {
            this.signature = signature;
            this.category = category;
        }

        private void add(LogObservation observation, Map<String, Integer> likelyMods) {
            count++;
            severity = Severity.max(severity, observation.severity());
            if (firstSeen == null || observation.timestamp().isBefore(firstSeen)) {
                firstSeen = observation.timestamp();
            }
            if (lastSeen == null || observation.timestamp().isAfter(lastSeen)) {
                lastSeen = observation.timestamp();
            }
            loggerCounts.merge(observation.loggerName().isBlank() ? "unknown" : observation.loggerName(), 1, Integer::sum);
            likelyMods.forEach((modId, weight) -> likelyModCounts.merge(modId, weight, Integer::sum));
            if (examples.size() < 3 && !examples.contains(observation.message())) {
                examples.add(observation.message());
            }
            addKnownIssueSuggestions(observation);
            addMissingClassEvidence(observation);
            addRecipeEvidence(observation);
            addRegistryEvidence(observation);
            addResourcePackEvidence(observation);
            addMixinEvidence(observation);
            addMissingResourceEvidence(observation);
            addBlockstateVariantSuggestion(observation);
            addResourceReloadEvidence(observation);
            addDependencyEvidence(observation);
            addPerformanceEvidence(observation);
            addFeatureDegradationEvidence(observation);
        }

        private IssueSnapshot snapshot() {
            return new IssueSnapshot(
                    signature,
                    category,
                    severity,
                    count,
                    firstSeen,
                    lastSeen,
                    sortCounts(loggerCounts),
                    sortCounts(likelyModCounts),
                    List.copyOf(examples),
                    List.copyOf(evidence),
                    suggestions()
            );
        }

        private List<FixSuggestion> suggestions() {
            List<FixSuggestion> suggestions = new ArrayList<>(knownIssueSuggestions);
            suggestions.addAll(resolvableSuggestions());
            suggestions.addAll(loggingSuppressionSuggestions());
            suggestions.addAll(SuggestionEngine.suggestionsFor(category));
            return List.copyOf(suggestions);
        }

        private List<FixSuggestion> resolvableSuggestions() {
            if (category != IssueCategory.RECIPE) {
                return List.of();
            }

            List<String> recipeIds = evidenceValues("Recipe id");
            List<String> missingKeys = evidenceValues("Missing registry key");
            if (recipeIds.isEmpty() || missingKeys.isEmpty()) {
                return List.of();
            }

            String missingNamespaces = missingKeys.stream()
                    .map(LogIssueAnalyzer::namespaceOf)
                    .distinct()
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("unknown");
            String recipeNamespace = recipeIds.stream()
                    .map(LogIssueAnalyzer::namespaceOf)
                    .findFirst()
                    .orElse("unknown");
            String detail = "The log names concrete recipe IDs from `" + recipeNamespace
                    + "` that reference a registry entry from `" + missingNamespaces
                    + "` which is not loaded. This is usually stale optional integration data: either install the mod that provides the missing registry entry, update the recipe-owning mod, or remove only the broken compat recipes with KubeJS.";
            return List.of(new FixSuggestion(
                    "kubejs-draft",
                    "Remove recipes that reference missing optional integration keys",
                    detail,
                    "",
                    "Copy KubeJS removal draft",
                    kubeJsRecipeRemovalDraft(recipeIds, missingKeys)
            ));
        }

        private List<String> evidenceValues(String label) {
            String prefix = label + ": ";
            return evidence.stream()
                    .filter(value -> value.startsWith(prefix))
                    .map(value -> value.substring(prefix.length()))
                    .distinct()
                    .toList();
        }

        private String kubeJsRecipeRemovalDraft(List<String> recipeIds, List<String> missingKeys) {
            StringBuilder builder = new StringBuilder();
            builder.append("// kubejs/server_scripts/pack_log_doctor_recipe_fixes.js\n");
            builder.append("// Generated draft: recipes below reference missing registry keys seen in the log.\n");
            builder.append("// Missing keys: ").append(String.join(", ", missingKeys)).append('\n');
            builder.append("// Review before enabling. This removes broken recipes; it does not replace their crafting paths.\n");
            builder.append("ServerEvents.recipes(event => {\n");
            for (String recipeId : recipeIds) {
                builder.append("  event.remove({ id: '").append(recipeId).append("' });\n");
            }
            builder.append("});\n");
            return builder.toString();
        }

        private List<FixSuggestion> loggingSuppressionSuggestions() {
            if (count < 20 || examples.isEmpty()) {
                return List.of();
            }
            String logger = sortCounts(loggerCounts).keySet().stream().findFirst().orElse("unknown");
            String messageContains = examples.getFirst()
                    .replace('\n', ' ')
                    .replace('\r', ' ')
                    .strip();
            if (messageContains.length() > 180) {
                messageContains = messageContains.substring(0, 180);
            }
            String detail = "This exact issue group repeated " + count
                    + " times. If you have already decided it is harmless duplicate spam, suppress the specific logger/message substring rather than lowering the whole log level for the mod.";
            String body = """
                    # Pack Log Doctor logger suppression candidate
                    # This is not applied automatically. Use it as review evidence for a very narrow Log4j filter.
                    # Prefer fixing or reporting the root cause when the message points to missing assets/data.
                    Logger: %s
                    Message contains: %s
                    Group signature: %s
                    Count in report: %s
                    """.formatted(logger, messageContains, signature, count);
            return List.of(new FixSuggestion(
                    "logging-draft",
                    "Suppress only this repeated logger/message after review",
                    detail,
                    "",
                    "Copy logger suppression candidate",
                    body
            ));
        }

        private void addKnownIssueSuggestions(LogObservation observation) {
            for (FixSuggestion suggestion : KnownIssueEngine.suggestionsFor(observation)) {
                if (!knownIssueSuggestions.contains(suggestion)) {
                    knownIssueSuggestions.add(suggestion);
                }
            }
        }

        private void addMissingClassEvidence(LogObservation observation) {
            String text = observation.message() + "\n" + observation.throwableSummary();
            addPatternEvidence("Missing class", MISSING_CLASS.matcher(text));
        }

        private void addRecipeEvidence(LogObservation observation) {
            if (category != IssueCategory.RECIPE || evidence.size() >= 5) {
                return;
            }
            addPatternEvidence("Recipe id", RECIPE_LOAD_ERROR.matcher(observation.message()));
            addPatternEvidence("Missing registry key", UNKNOWN_REGISTRY_KEY.matcher(observation.message()));
        }

        private void addRegistryEvidence(LogObservation observation) {
            if (category != IssueCategory.REGISTRY || evidence.size() >= 5) {
                return;
            }
            Matcher matcher = LOOT_REGISTRY_ERROR.matcher(observation.message());
            while (matcher.find() && evidence.size() < 5) {
                addEvidence("Registry element: " + matcher.group(1));
                addEvidence("Missing registry key: " + matcher.group(2));
            }
            Matcher lootTable = LOOT_TABLE_VALIDATION.matcher(observation.message());
            while (lootTable.find() && evidence.size() < 5) {
                addEvidence("Loot table: " + lootTable.group(1));
                addEvidence("Missing loot table: " + lootTable.group(2));
            }
        }

        private void addResourcePackEvidence(LogObservation observation) {
            if (category != IssueCategory.RESOURCE_PACK || evidence.size() >= 5) {
                return;
            }
            addPatternEvidence("Invalid pack path", INVALID_PACK_PATH.matcher(observation.message()));
        }

        private void addMixinEvidence(LogObservation observation) {
            if (category != IssueCategory.MIXIN || evidence.size() >= 5) {
                return;
            }
            String text = observation.message() + "\n" + observation.throwableSummary();
            addPatternEvidence("Mixin target", MIXIN_TARGET.matcher(text));
            addPatternEvidence("Mixin config", MIXIN_CONFIG.matcher(text));
            addPatternEvidence("Mixin owner", MIXIN_FROM_MOD.matcher(text));
        }

        private void addMissingResourceEvidence(LogObservation observation) {
            if (category != IssueCategory.MISSING_RESOURCE || evidence.size() >= 5) {
                return;
            }
            Matcher matcher = MISSING_BLOCKSTATE_MODEL.matcher(observation.message());
            if (!matcher.find()) {
                return;
            }
            addEvidence("Blockstate file: " + matcher.group(1));
            addEvidence("Missing model variant: " + matcher.group(2));
        }

        private void addBlockstateVariantSuggestion(LogObservation observation) {
            if (category != IssueCategory.MISSING_RESOURCE) {
                return;
            }
            Matcher matcher = MISSING_BLOCKSTATE_MODEL.matcher(observation.message());
            if (!matcher.find()) {
                return;
            }
            String blockstateFile = matcher.group(1);
            String variant = matcher.group(2);
            String namespace = namespaceOf(blockstateFile);
            String detail = "Minecraft is loading a blockstate JSON from namespace `" + namespace
                    + "`, but that JSON does not cover the generated variant `" + variant
                    + "`. When this repeats many times for the same block, the likely cause is a block with properties whose possible states outnumber the packaged model definitions. This is inferred from the log; check the addon owning namespace `"
                    + namespace + "` or patch the missing blockstate/model assets with a resource pack.";
            FixSuggestion suggestion = new FixSuggestion(
                    "log-inferred",
                    "Blockstate variant/model mismatch in " + namespace,
                    detail
            );
            if (!knownIssueSuggestions.contains(suggestion)) {
                knownIssueSuggestions.add(suggestion);
            }
        }

        private void addResourceReloadEvidence(LogObservation observation) {
            if (category != IssueCategory.RESOURCE_RELOAD || evidence.size() >= 5) {
                return;
            }
            String text = observation.message() + "\n" + observation.throwableSummary();
            if (text.toLowerCase(Locale.ROOT).contains("registerreloadlistener")) {
                addEvidence("ModernFix diagnostic: registerReloadListener called during resource reload");
            }
            Matcher matcher = TRANSFORMER_MOD.matcher(text);
            while (matcher.find() && evidence.size() < 5) {
                String owner = matcher.group(1).toLowerCase(Locale.ROOT);
                if (!owner.equals("minecraft")) {
                    addEvidence("Resource reload owner: " + matcher.group(1));
                }
            }
        }

        private void addDependencyEvidence(LogObservation observation) {
            if (category != IssueCategory.MISSING_DEPENDENCY || evidence.size() >= 5) {
                return;
            }
            String text = observation.message() + "\n" + observation.throwableSummary();
            Matcher conflict = MISSING_OR_UNSUPPORTED_DEPENDENCY.matcher(text);
            while (conflict.find() && evidence.size() < 5) {
                addEvidence("Dependency: " + conflict.group(1));
                addEvidence("Requested by: " + conflict.group(2));
                addEvidence("Expected range: " + conflict.group(3));
                addEvidence("Actual version: " + conflict.group(4));
            }
            Matcher preload = PRELOAD_DEPENDENCY_ERROR.matcher(text);
            while (preload.find() && evidence.size() < 5) {
                addEvidence("Dependency failure: " + preload.group(1) + " requires "
                        + preload.group(2) + " " + preload.group(3).strip());
            }
        }

        private void addPerformanceEvidence(LogObservation observation) {
            if (category != IssueCategory.PERFORMANCE || evidence.size() >= 5) {
                return;
            }
            String text = observation.message() + "\n" + observation.throwableSummary();
            Matcher jei = JEI_LONG_TASK.matcher(text);
            while (jei.find() && evidence.size() < 5) {
                addEvidence("Long JEI task: " + jei.group(1) + " for " + jei.group(2)
                        + " took at least " + jei.group(3) + " seconds");
            }
            Matcher memory = DEBUG_GUARDIAN_HIGH_MEMORY.matcher(text);
            while (memory.find() && evidence.size() < 5) {
                addEvidence("Memory pressure: " + memory.group(1) + " MB used, threshold " + memory.group(2) + " MB");
            }
            Matcher hang = DEBUG_GUARDIAN_WORLD_HANG.matcher(text);
            while (hang.find() && evidence.size() < 5) {
                addEvidence("World hang report: " + hang.group(1).strip());
                addEvidence("Top hang suspect: " + hang.group(2).strip());
            }
            Matcher unresponsive = SERVER_UNRESPONSIVE.matcher(text);
            while (unresponsive.find() && evidence.size() < 5) {
                addEvidence("Server thread state: " + unresponsive.group(1)
                        + " for " + unresponsive.group(2) + " ms");
            }
            Matcher keepUp = CANT_KEEP_UP.matcher(text);
            while (keepUp.find() && evidence.size() < 5) {
                addEvidence("Server delay: " + keepUp.group(1) + " ms / " + keepUp.group(2) + " ticks behind");
            }
        }

        private void addFeatureDegradationEvidence(LogObservation observation) {
            if (category != IssueCategory.FEATURE_DEGRADATION || evidence.size() >= 5) {
                return;
            }
            String text = observation.message() + "\n" + observation.throwableSummary();
            Matcher emptyGroup = JEI_EMPTY_GROUP.matcher(text);
            while (emptyGroup.find() && evidence.size() < 5) {
                addEvidence("JEI missing item group: " + emptyGroup.group(1).strip());
            }
            Matcher patchouli = PATCHOULI_MISSING_RECIPE.matcher(text);
            while (patchouli.find() && evidence.size() < 5) {
                addEvidence("Guidebook missing recipe: " + patchouli.group(1));
                addEvidence("Recipe type: " + patchouli.group(2));
            }
            Matcher sound = MISSING_SOUND.matcher(text);
            while (sound.find() && evidence.size() < 5) {
                addEvidence("Missing sound event: " + sound.group(1));
            }
            Matcher shader = SHADER_MISMATCH.matcher(text);
            while (shader.find() && evidence.size() < 5) {
                addEvidence("Shader program: " + shader.group(1));
                addEvidence("Missing shader " + shader.group(2).toLowerCase(Locale.ROOT) + ": " + shader.group(3));
            }
            Matcher sprite = INVALID_SPRITE_FRAME.matcher(text);
            while (sprite.find() && evidence.size() < 5) {
                addEvidence("Animated sprite: " + sprite.group(1));
                addEvidence("Invalid frame index: " + sprite.group(2));
            }
        }

        private void addPatternEvidence(String label, Matcher matcher) {
            while (matcher.find() && evidence.size() < 5) {
                addEvidence(label + ": " + matcher.group(1));
            }
        }

        private void addEvidence(String value) {
            if (evidence.size() < 5 && !evidence.contains(value)) {
                evidence.add(value);
            }
        }
    }
}
