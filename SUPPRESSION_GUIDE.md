# PackLogDoctor Baseline Guide

PackLogDoctor can classify known or expected errors during report generation, so you can focus on new issues without deleting evidence from the full report.

## How It Works

1. Run your Minecraft instance with PackLogDoctor. It writes full reports in `pack-log-doctor/`.
2. Review `pack-log-doctor/report.md` or `report.html` and decide which repeated issue groups are known upstream noise, pack issues to fix later, or investigation candidates.
3. Put shared pack rules in `config/packlogdoctor-issue-baseline.json`, or local-only rules in `pack-log-doctor/issue-baseline.json`.
4. Inspect `pack-log-doctor/unexpected-report.md` or `unexpected-report.json` after the next run. WARN issue groups that do not match the baseline appear in the unexpected section. ERROR and FATAL issue groups always remain visible there, even when a baseline rule classifies them.

The full report still contains all warnings and errors. The baseline only changes the derived unexpected report for warnings; errors and fatal issues are classified but not hidden.

## Example

```json
{
  "rules": [
    {
      "status": "known_upstream",
      "loggerPattern": "mixin",
      "messageContains": "Discarding @Unique public method",
      "note": "Reviewed Copycats mixin duplicate method noise."
    },
    {
      "status": "needs_fix",
      "loggerPattern": "net.minecraft.world.item.crafting.RecipeManager",
      "messageContains": "examplemod:broken_recipe",
      "note": "Remove or repair this stale optional compat recipe."
    }
  ]
}
```

## Status Values

`status` is free-form, but these values are recommended:

- `known_upstream`: Third-party mod noise that should be reported upstream or ignored for now.
- `known_environment`: Local machine or driver warning.
- `known_compat`: Expected cross-mod limitation.
- `needs_fix`: Pack-owned or pack-fixable issue already accepted into the backlog.
- `needs_investigation`: Reviewed issue that still needs root-cause work.

Only unmatched warning groups are treated as `unexpected`. Matched errors and fatal issues are still shown in the unexpected report so severe failures do not disappear behind a known rule.

## Match Fields

- `signature`: Exact issue-group signature from `report.md` or `report.json`. This is the narrowest rule.
- `loggerPattern`: Logger name with optional `*` wildcards, such as `mixin` or `net.minecraft.*`.
- `messageContains`: Case-sensitive substring matched against issue examples.
- `note`: Human review note explaining why the issue is classified.

When a rule has multiple match fields, all of them must match.

## Tips

- Prefer narrow `loggerPattern` plus `messageContains` rules over whole-logger rules.
- Use `needs_fix` for accepted backlog items so they stop appearing as new surprises but remain visible in the full report.
- Use local `pack-log-doctor/issue-baseline.json` for experiments before committing shared pack rules.
- Do not use Log4j filtering as the main workflow on NeoForge launchers that overwrite custom Log4j configuration.

## Finding Logger Names

1. Run PackLogDoctor and generate a report.
2. Open `report.json`, `report.md`, or `report.html`.
3. Check the "Loggers" section under each issue.
4. Use those logger names in baseline rules.
