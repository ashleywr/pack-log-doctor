# Pack Log Doctor Design

## What it can do now

Pack Log Doctor installs a Log4j appender during mod construction. Each warning or error becomes a small `LogObservation` and flows through the pure analyzer. The analyzer normalizes repeated messages, records counts, tracks logger names, stores representative examples, and estimates likely mod ownership.

Reports are written on demand and at JVM shutdown:

- `pack-log-doctor/report.md`
- `pack-log-doctor/report.json`

## Attribution heuristics

Attribution is probabilistic. The analyzer prefers:

1. resource IDs in the message, such as `modid:path`
2. logger names containing a loaded mod ID
3. thrown stack trace class packages containing a loaded mod ID

The report should label this as "likely" ownership, not proof.

## Future remediation model

Automatic fixes should be explicit rules, not generated patches:

- matcher: normalized log signature, logger, severity, and optional mod/version range
- diagnosis: what is likely wrong
- action: one or more suggested fixes
- safety: read-only, draft-file, config-change, kubejs-draft, mixin-rule

Only `read-only` and `draft-file` actions should be enabled by default. Runtime mixin fixes should require a curated allowlist because they depend on exact class load timing and bytecode targets.
