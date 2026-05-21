---
name: integration-test-verification
description: Use when running, selecting, authoring, or triaging integration tests, Kotest specs, Gradle matrix tasks, FakePlayer-backed test cases, or compact test-result files; do not use for unrelated production-only edits, release publishing, or general PR prose.
---

# Integration Test Verification

Use this skill for the repository's Paper-backed integration-test harness and for deciding how to validate a change without opening large server logs by default.

## When to use

- Selecting `./gradlew integrationTest` versus a version-limited matrix run.
- Adding or changing Kotlin integration tests under `src/integrationTest/kotlin`.
- Registering a new spec in `KotestRunner`.
- Triaging `checkTestResults<version>` output or `plugins/OldCombatMechanicsTest/test-failures.txt`.
- Reasoning about tests that use `FakePlayer`, synthetic Bukkit events, PacketEvents wrappers, or direct module handler calls.

## When not to use

- Do not use for release asset, Hangar, CurseForge, or licence checks; use `release-readiness-review`.
- Do not use for module assignment or config migration design unless the task is specifically about test coverage; use `module-config-change`.
- Do not use for broad Java 8/NMS design unless the immediate question is test-harness behaviour; use `compatibility-strategy`.

## Non-negotiable rules

- Root and user-facing agents must not read `build/integration-test-logs/*.log` directly; log inspection is allowed only by subagents, and only when needed for integration-test triage or when explicitly requested.
- Prefer compact console output and `plugins/OldCombatMechanicsTest/test-failures.txt` first for failure triage.
- Do not disable, skip, weaken, or version-gate tests as a way to make validation pass. Fix the root cause, or report the failure as a blocker unless the user explicitly approves a coverage reduction.
- New integration specs must be added to `KotestRunner.withClasses(...)`; autoscan is disabled.
- Keep paths relative to the repository root, plugin data folder, or server run directory.
- Avoid starting servers for documentation-only or opencode-config-only changes.

## Harness quick reference

- Full matrix: `./gradlew integrationTest`.
- Selected matrix: `./gradlew integrationTest -PintegrationTestVersions=1.19.2,1.21.11,1.12`.
- Wrapper helper: `scripts/run-integration-matrix.sh -v 1.19.2,1.21.11 [-s '*SpecName*'] [-t '*test name*']` keeps Gradle and Kotest filter arguments consistent.
- Compact result summary: `scripts/summarise-integration-results.sh` reads only `run/*/plugins/OldCombatMechanicsTest/test-results.txt` and `test-failures.txt`; it does not open full server logs.
- Matrix suggestion helper: `scripts/suggest-integration-matrix.sh [changed-path ...]` gives a conservative starting point for versions and filters.
- Individual task names follow `integrationTest<version>` with punctuation mapped to underscores, for example `integrationTest1_19_2`.
- Test plugin entrypoint: `kernitus.plugin.OldCombatMechanics.OCMTestMain`.
- Success handoff: `plugins/OldCombatMechanicsTest/test-results.txt` contains `PASS`.
- Failure summary: `plugins/OldCombatMechanicsTest/test-failures.txt` contains a compact list, capped for CI readability.
- Java 11+ server targets use Kotest 6 (`KotestRunner`, `KotestProjectConfig`).
- Java 8 targets use `LegacyTestRunner`, currently only a smoke path.

## Validation selection template

1. Identify the changed feature and affected server bands.
2. Prefer the narrowest version set that covers the compatibility boundary.
3. If the change touches packet handling, include a modern PacketEvents-covered version.
4. If the change touches legacy behaviour or Java 8 APIs, include `1.12` where practical.
5. If the change is documentation or opencode-only, run filesystem/schema checks rather than server tests.

Example recommendation:

```text
Run: ./gradlew integrationTest -PintegrationTestVersions=1.19.2,1.21.11
Reason: covers modern Paper plus the PacketEvents path affected by the change.
Avoid: opening build/integration-test-logs from a root or user-facing agent; use a subagent only when compact failure output is insufficient or log inspection is explicitly requested.
```

## Known shortcut patterns

Some tests intentionally exercise module code without fully realistic gameplay plumbing:

- `GoldenAppleIntegrationTest`, `OldPotionEffectsIntegrationTest`, `OldArmourDurabilityIntegrationTest`, `PlayerKnockbackIntegrationTest`, `SwordBlockingIntegrationTest`, and `SwordSweepIntegrationTest` use synthetic events in places.
- `PlayerKnockbackIntegrationTest` and `SwordSweepIntegrationTest` directly invoke module handlers.
- Packet cancellation tests construct and dispatch PacketEvents events directly for deterministic coverage.

Treat these shortcuts as design choices unless the task asks for end-to-end realism.

## References

- Historical integration and regression notes: `references/relocated-agents-notes.md`.
