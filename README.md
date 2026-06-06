# Pack Log Doctor

Pack Log Doctor is a NeoForge 1.21.1 utility mod for modpack makers and mod developers. It watches Minecraft's warning and error logs, groups repeated messages, guesses which mods are involved, and writes compact diagnostics reports.

The initial goal is to reduce raw-log reading and make Codex/debugging sessions cheaper:

- show the loudest log sources and likely mod IDs
- group repeated warnings/errors by normalized signature
- preserve representative examples and stack traces
- classify common patterns like missing resources, broken recipes, mixin warnings, and missing references
- write `pack-log-doctor/report.md` and `pack-log-doctor/report.json` under the game directory
- generate fix suggestions as data before any automatic mutation is allowed

## Boundaries

This mod can only capture logs after it is loaded. Very early loader failures still need launcher logs or a future Java agent/launcher helper.

Automatic mixins are intentionally not generated from arbitrary log messages. Future remediation should be opt-in and rule based: known problem, known affected version range, known patch or KubeJS suggestion.

## Build

```powershell
.\gradlew.bat build
```

If no Gradle wrapper is present yet, copy one from a nearby NeoForge project or run with an installed Gradle distribution.
