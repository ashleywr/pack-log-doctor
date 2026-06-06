package com.sanhiruzu.packlogdoctor.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class KnownIssueEngine {
    private KnownIssueEngine() {
    }

    static List<FixSuggestion> suggestionsFor(LogObservation observation) {
        String text = (observation.message() + "\n" + observation.throwableSummary()).toLowerCase(Locale.ROOT);
        List<FixSuggestion> suggestions = new ArrayList<>();

        if (text.contains("spawn_notification")
                && text.contains("tim_core")
                && text.contains("cobblemon")
                && (text.contains("arrayindexoutofboundsexception")
                || text.contains("hard_dep")
                || text.contains("failed to create mod instance"))) {
            suggestions.add(new FixSuggestion(
                    "known-issue",
                    "Spawn Notification compatibility with Cobblemon 1.7.3",
                    "This matches a known Spawn Notification failure family involving Cobblemon 1.7.3. Tim Core appears in the stack as Spawn Notification's support library/dependency, but the first mod to remove or replace is Spawn Notification. Related upstream issue: https://github.com/timinc-cobble/cobblemon-spawn-notification/issues/28"
            ));
        }

        if (text.contains("gtceu")
                && text.contains("kubejs")
                && text.contains("dev/latvian/mods/kubejs/bindings/event/serverevents")
                && (text.contains("noclassdeffounderror") || text.contains("classnotfoundexception"))) {
            suggestions.add(new FixSuggestion(
                    "version-alignment",
                    "GTCEu is calling a KubeJS class that is not present",
                    "GTCEu reached its recipe setup path while creating/loading a world, then tried to use dev.latvian.mods.kubejs.bindings.event.ServerEvents. The installed KubeJS build does not provide that class, so a KubeJS script cannot patch this at runtime. Align GTCEu with the KubeJS version it supports, downgrade KubeJS to the build expected by GTCEu, or temporarily remove GTCEu/KubeJS integration until the versions match."
            ));
        }

        if (text.contains("createdeco")
                && text.contains("jsonparseexception")
                && text.contains("failed to parse either")
                && text.contains("minecraft:white_dye")
                && (text.contains("createdeco:placard") || text.contains("recipe"))) {
            suggestions.add(new FixSuggestion(
                    "known-issue",
                    "Create Deco placard recipe fails on NeoForge 1.21.1",
                    "This matches Create Deco issue #268: the createdeco:placard recipe uses an ingredient shape that the 1.21.1 recipe parser rejects. Until the mod ships a fixed recipe, a pack-side workaround is to remove or replace that recipe with KubeJS/datapack content. This is safe to draft because it targets one broken recipe, but it changes gameplay by removing or changing placard crafting.",
                    "https://github.com/talrey/CreateDeco/issues/268",
                    "Copy KubeJS workaround draft",
                    """
                            // kubejs/server_scripts/pack_log_doctor_createdeco_placard_fix.js
                            // Workaround for Create Deco issue #268 on Minecraft 1.21.1 / NeoForge:
                            // https://github.com/talrey/CreateDeco/issues/268
                            // This removes the broken recipe so world loading can continue.
                            // Replace it with a corrected shaped/shapeless recipe if you want placards craftable.
                            ServerEvents.recipes(event => {
                              event.remove({ id: 'createdeco:placard' });
                            });
                            """
            ));
        }

        return suggestions;
    }
}
