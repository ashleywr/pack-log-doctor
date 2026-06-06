package com.sanhiruzu.packlogdoctor.analysis;

import java.util.List;

final class SuggestionEngine {
    private SuggestionEngine() {
    }

    static List<FixSuggestion> suggestionsFor(IssueCategory category) {
        return switch (category) {
            case KUBEJS -> List.of(new FixSuggestion(
                    "draft-file",
                    "Inspect generated KubeJS script errors",
                    "Keep this read-only until the exact failing script and event are known. A future rule can generate a disabled draft under kubejs/server_scripts/_pack_log_doctor_drafts/."));
            case MISSING_RESOURCE -> List.of(new FixSuggestion(
                    "read-only",
                    "Check resource ownership",
                    "Look for the namespace in the message. Missing textures/models are often harmless, but repeated spam should be reported to the owning mod or patched with a resource pack."));
            case MIXIN -> List.of(new FixSuggestion(
                    "read-only",
                    "Do not auto-generate mixins",
                    "Mixin warnings depend on exact bytecode and load timing. Only curated compatibility mixins should ever be enabled automatically."));
            case RECIPE -> List.of(new FixSuggestion(
                    "kubejs-draft",
                    "Generate recipe remove/replace candidate",
                    "If the report includes a concrete recipe ID, a future rule can draft a KubeJS removal or replacement snippet for review."));
            case DATAPACK -> List.of(new FixSuggestion(
                    "draft-file",
                    "Generate tag/datapack candidate",
                    "If a missing tag or malformed ID is present, a future rule can draft a datapack or KubeJS tag patch."));
            case MISSING_DEPENDENCY -> List.of(new FixSuggestion(
                    "read-only",
                    "Install, re-enable, update, or remove the requesting mod",
                    "If the report names a missing mandatory dependency, either add/re-enable a compatible dependency version or disable the mod that requested it. This failure happens before normal gameplay fixes or scripts can run."));
            case MOD_LOADING -> List.of(new FixSuggestion(
                    "read-only",
                    "Disable or update the failed mod first",
                    "A mod loading failure usually names the failing mod directly. Remove or update that mod before chasing later broken-state errors."));
            case RESOURCE_RELOAD -> List.of(new FixSuggestion(
                    "read-only",
                    "Check resource reload listener timing",
                    "A mod registered a reload listener while resources were already reloading. This can leave startup/resource screens in confusing states or cause reload races; update or temporarily remove the owning mod first."));
            case RESOURCE_PACK -> List.of(new FixSuggestion(
                    "read-only",
                    "Report packaged resource path issue",
                    "This usually means a mod jar includes files with names Minecraft rejects in resource packs, such as uppercase LICENSE files under assets. It is normally harmless unless the rejected path is an actual asset the game needs."));
            case PERFORMANCE -> List.of(new FixSuggestion(
                    "read-only",
                    "Treat as a launch or world performance suspect",
                    "This is not necessarily a crash cause, but it explains slow screens, world stalls, or memory pressure. Check the evidence for the subsystem and compare it against recently changed mods before disabling broad groups."));
            case FEATURE_DEGRADATION -> List.of(new FixSuggestion(
                    "read-only",
                    "Expect a visible feature to be missing or incomplete",
                    "The game may launch, but the named feature can be broken: guidebook entries, JEI ingredients, sounds, animations, shaders, or resource-backed content may be absent. Use the evidence to decide whether to ignore, patch with a resource/data pack, or report upstream."));
            default -> List.of();
        };
    }
}
