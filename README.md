# Pack Log Doctor

Pack Log Doctor is a NeoForge 1.21.1 utility mod for modpack makers and mod developers. It watches Minecraft's warning and error logs, groups repeated messages, guesses which mods are involved, and writes compact diagnostics reports.

The initial goal is to reduce raw-log reading and make Codex/debugging sessions cheaper:

- show the loudest log sources and likely mod IDs
- group repeated warnings/errors by normalized signature
- preserve representative examples and stack traces
- classify common patterns like missing resources, broken recipes, mixin warnings, and missing references
- write `pack-log-doctor/report.md` and `pack-log-doctor/report.json` under the game directory
- write `pack-log-doctor/unexpected-report.md` and `pack-log-doctor/unexpected-report.json` after applying a reviewed issue baseline
- generate fix suggestions as data before any automatic mutation is allowed

## Boundaries

Runtime capture only sees logs after the mod is loaded. Report generation also reads `logs/latest.log` when it exists, so normal reports can include earlier loader warnings and errors. Very early crashes that prevent a log file from being written still need launcher logs or a future Java agent/launcher helper.

Automatic mixins are intentionally not generated from arbitrary log messages. Future remediation should be opt-in and rule based: known problem, known affected version range, known patch or KubeJS suggestion.

## Issue Baseline

Pack Log Doctor can apply a reviewed baseline so repeated known warnings do not bury new issues. Put shared pack rules in:

```text
config/packlogdoctor-issue-baseline.json
```

For local-only experiments, put overrides in:

```text
pack-log-doctor/issue-baseline.json
```

The local file wins when present. Baseline rules support `status`, `signature`, `loggerPattern`, `messageContains`, and `note`. Issues with no matching rule remain `unexpected` and appear in `unexpected-report.md/json`. Known issues are summarized there and still remain visible in the full report.

## Build

```powershell
.\gradlew.bat build
```

If no Gradle wrapper is present yet, copy one from a nearby NeoForge project or run with an installed Gradle distribution.
