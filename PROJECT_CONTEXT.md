# Pack Log Doctor Project Context

## Why this exists

Pack Log Doctor is being built to make modpack and Minecraft mod development less dependent on manually reading huge logs.

The immediate user workflow is AMI compatibility testing: trying lots of unfamiliar mods, many of which may be outdated, noisy, broken, or only partially compatible. When something fails, the current process often means trial and error, scanning long logs, or spending Codex context by pasting raw log output.

This mod should answer the practical debugging questions faster:

- what warnings/errors happened?
- which messages are repeated spam?
- which mods or namespaces are probably involved?
- which mods are the loudest?
- what issue categories should be inspected first?
- what compact report can be given to Codex or attached to a bug report?
- what safe fix suggestions can be generated later?

## What it is

Pack Log Doctor is a NeoForge 1.21.1 utility mod.

Project path:

```text
C:\WorkDir\Minecraft Mods\PackLogDoctor
```

It currently:

- installs a Log4j appender when the mod loads
- captures `WARN`, `ERROR`, and `FATAL` log events after the mod is loaded
- groups repeated messages by normalized signature
- classifies issues into categories
- guesses likely mod ownership
- writes compact reports
- creates `config/packlogdoctor.json` with report output and size limits
- exposes an operator command to write a report without waiting for shutdown

## Current reports

Reports are written under the active Minecraft game directory:

```text
pack-log-doctor/report.md
pack-log-doctor/report.json
```

Runtime config is written under the active Minecraft game directory:

```text
config/packlogdoctor.json
```

Current config fields:

```json
{
  "maxGroupedIssues": 50,
  "maxExamplesPerIssue": 3,
  "maxLoudestMods": 20,
  "writeMarkdown": true,
  "writeJson": true,
  "printSummaryToLog": true,
  "modpackName": "",
  "modpackVersion": ""
}
```

The command is:

```text
/packlogdoctor report
```

The report includes:

- generated timestamp
- trigger, such as `shutdown` or `command`
- loaded mod IDs known to the analyzer
- loudest likely mods
- grouped warnings and errors
- representative example messages
- issue category
- likely mod attribution counts
- environment and modpack metadata
- mixin evidence extracted from common mixin failure text
- fix suggestions where available
- known issue suggestions for recognized upstream failures, currently including the Spawn Notification compatibility failure family involving Cobblemon 1.7.3 and the Tim Core support library

## Current categories

The analyzer currently classifies issues as:

```text
MISSING_RESOURCE
MIXIN
RECIPE
DATAPACK
REGISTRY
MISSING_DEPENDENCY
MOD_LOADING
KUBEJS
UNKNOWN
```

Attribution is intentionally probabilistic. It uses:

- resource IDs like `modid:path`
- logger names
- stack trace/package text
- loaded mod IDs

Reports should treat ownership as "likely", not proof.

## Important files

```text
src/main/java/com/sanhiruzu/packlogdoctor/PackLogDoctor.java
src/main/java/com/sanhiruzu/packlogdoctor/runtime/LogDoctorRuntime.java
src/main/java/com/sanhiruzu/packlogdoctor/runtime/LogDoctorAppender.java
src/main/java/com/sanhiruzu/packlogdoctor/analysis/LogIssueAnalyzer.java
src/main/java/com/sanhiruzu/packlogdoctor/report/ReportWriter.java
src/test/java/com/sanhiruzu/packlogdoctor/analysis/LogIssueAnalyzerTest.java
docs/design.md
```

## Build and deploy

Build from the project root:

```powershell
cd "C:\WorkDir\Minecraft Mods\PackLogDoctor"
.\gradlew.bat build
```

The Gradle `build` task currently finalizes with `deployLocalModpack`, which copies the built jar to the AMICompat Prism instance:

```text
C:\Users\ashle\AppData\Roaming\PrismLauncher\instances\AMICompat\minecraft\mods
```

The deploy path is configured in:

```text
gradle.properties
```

Property:

```properties
local_deploy_mods_dir=C:/Users/ashle/AppData/Roaming/PrismLauncher/instances/AMICompat/minecraft/mods
```

Verified jar:

```text
packlogdoctor-neoforge-1.21.1-0.1.0.jar
```

## Verification already run

These passed:

```powershell
.\gradlew.bat test
.\gradlew.bat build
```

## Design boundary

This mod should not generate arbitrary mixins from log messages.

Mixin fixes are dangerous because they depend on exact class load timing, bytecode shape, mod versions, and target availability. Future mixin remediation should only be curated and opt-in:

```text
known problem
known affected mod/version range
known compatibility mixin
explicit enablement
```

KubeJS/datapack/config remediation is more realistic, but should start as generated draft suggestions, not silent mutation.

Preferred future fix model:

- matcher: normalized log signature, logger, severity, category, optional mod/version
- diagnosis: likely root cause
- action: read-only suggestion, draft file, KubeJS draft, datapack draft, config suggestion
- safety: default to read-only or draft-file

## Near-term next ideas

- Add a config file for report limits and deploy behavior.
- Add deploy behavior config if deployment needs to move out of Gradle properties.
- Capture a loaded mod metadata table with versions.
- Add more deterministic tests for common Minecraft log patterns.
- Add report sections for KubeJS, recipes, missing resources, and mixins.
- Add a CLI/offline analyzer that can read an existing `latest.log`.
- Add draft KubeJS snippets only when a concrete recipe/tag/resource ID is present.
- Consider a launcher-side helper later for very early loader failures that happen before the mod can load.
