# AGENTS.md

**Prime directive:** If you are reading this file, it is your responsibility as an agent to keep it up to date with any changes you make in this repository.

This file holds always-on repository guidance and routing hints. Detailed workflows and historical notes live in repo-local agent skills under `.agents/skills/`.

## Always-on rules

- Use British English spelling and phraseology at all times.
- Do **not** use American English spelling or phraseology.
- Use conventional commit format.
- Keep commits subject-only unless the user explicitly asks for a body.
- Release Please renders commit subjects into changelog entries, so subjects must be release-note-friendly and comprehensible without the type prefix or body.
- Prefer scoped subjects where useful, such as `feat(config): add default modesets for unlisted worlds`.
- For issue, ticket, or bug-report driven work, include the ticket number in the commit subject, such as `fix(modesets): correct stale stored world modesets (#865)`.
- Avoid vague subjects such as `feat: support ...`, `feat: migrate ...`, or `test: cover ...` when a clearer user-facing summary is possible.
- Root and user-facing agents must not open or read `build/integration-test-logs/*.log` directly. Log inspection is allowed only by subagents, and only when needed for integration-test triage or when explicitly requested. Prefer compact console summaries and `plugins/OldCombatMechanicsTest/test-failures.txt` first.
- Never hard-code absolute filesystem paths in tests or production code; resolve locations relative to the repo root, plugin data folder, or server run directory.
- Prefer feature detection over hard-coded Minecraft version gates because server implementations may backport APIs.
- For NMS/reflection access, prefer the project `utilities.reflection.Reflector` helpers over ad-hoc reflection where practical.
- Reflection should be a compatibility fallback, not the first choice on hot paths.
- For optional methods on already-present Bukkit types, especially in hot paths, prefer direct calls with cached `NoSuchMethodError` or narrow linkage fallbacks over reflective invocation. Use reflection when classes, parameter or return types, signatures, NMS shapes, or cross-version linkage would make direct calls unsafe.
- Do not gate plugin behaviour on assumptions from one Paper/Spigot version without checking compatible API presence.
- Agents must not disable, skip, weaken, or version-gate tests as a way to make validation pass. Fix failing tests at the root cause, or report the failure as a blocker unless the user explicitly approves a coverage reduction.

## Repo overview

- Project: OldCombatMechanics, a Bukkit/Paper plugin.
- Branch context: working from `kotlin-tests`.
- Build tool: Gradle wrapper, currently 9.2.1.
- JDKs used locally: 8, 11, 17, 25.
- Main integration tests live in `src/integrationTest/kotlin` and are packaged into `OldCombatMechanics-<version>-tests.jar`.
- Entrypoint test plugin class: `kernitus.plugin.OldCombatMechanics.OCMTestMain`.
- PacketEvents is shaded into the main plugin; integration tests resolve PacketEvents from that shaded copy rather than injecting a separate external packet library.

## Build and validation quick reference

- Full integration matrix: `./gradlew integrationTest`.
- Selected versions: `./gradlew integrationTest -PintegrationTestVersions=1.19.2,1.21.11,1.12`.
- Matrix task examples: `integrationTest1_19_2`, `integrationTest1_21_11`, `integrationTest1_12`, `integrationTest1_9`.
- Java toolchain override example: `ORG_GRADLE_JAVA_INSTALLATIONS_PATHS=/path/to/jdk8:/path/to/jdk17:/path/to/jdk25 ./gradlew integrationTest`.
- `checkTestResults<version>` fails the build if `plugins/OldCombatMechanicsTest/test-results.txt` is missing or not `PASS`.
- `KotestRunner` writes a compact `plugins/OldCombatMechanicsTest/test-failures.txt` summary for CI-friendly failure triage.

## Skill selection guide

- **Mandatory trigger:** Use `dependabot-pr-review` for Dependabot PRs, dependency bumps, Gradle/Maven dependency updates, GitHub Actions version updates, dependency changelog/licence/release-note review, and dependency validation recommendations. Other skills remain relevant for deeper integration-test selection (`integration-test-verification`), compatibility strategy (`compatibility-strategy`), and release-readiness concerns (`release-readiness-review`).
- **Mandatory trigger:** Use `github-issue-investigation` for GitHub issue triage, `gh issue` investigation, picking a fresh issue, reproducing issue reports, or identifying durable regression coverage. Do not use it for GitHub-side mutation or implementation once the investigation is complete.
- **Mandatory trigger:** Use `integration-test-verification` for integration-test runs, matrix selection, compact failure triage, Kotlin integration test authoring, or `KotestRunner` registration questions. Do not use it for ordinary unit-free source edits with no test-harness impact.
- **Mandatory trigger:** Use `compatibility-strategy` for Java 8 compatibility, Bukkit/Paper version differences, NMS/reflection, fake players, PacketEvents compatibility, feature detection, or legacy-server fallbacks. Do not use it for release notes or configuration-only routing.
- **Mandatory trigger:** Use `module-config-change` for `config.yml`, module enablement, modesets, migrations, configurable module assignment, or per-module settings such as tool damage, attack cooldown, attack range, or sword blocking options. Do not use it for pure test execution or release publishing.
- **Mandatory trigger:** Use `commit-preparation` for preparing commits, pre-commit hooks, Spotless, formatting or linting, validation before commit, staging files, and conventional commit messages. Do not use it for implementation design or PR prose unless commit readiness is also in scope.
- **Mandatory trigger:** Use `release-readiness-review` for GitHub release, Hangar, CurseForge/BukkitDev, licence, workflow, supported-version, or release asset checks. Do not use it for feature implementation unless the change affects release packaging.
- **Mandatory trigger:** Use `pr-draft-summary` when drafting PR descriptions, change summaries, reviewer notes, or risk/test sections. Do not use it while still deciding implementation strategy.

## Current project state

- Paper 1.12 integration tests do not yet run the full fake-player test suite; legacy fake-player work needs a dedicated or version-aware NMS path.
- Java 8 compatibility is required for main code. Avoid APIs such as records, pattern matching, `Stream.toList()`, and `Set.of`/`List.of` in Java 8-targeted code.
- Main Java/Kotlin compilation targets Java 8 (`options.release.set(8)`, Kotlin `jvmTarget = 1.8`).
- Pre-1.13 integration-test versions use `integrationTestJavaVersionLegacyPre13` (default 8); modern versions use Java 25 for `>=1.20.5` and Java 17 otherwise.
- Legacy vanilla jars for `<=1.12` are downloaded by `downloadVanilla<version>` into `run/<version>/cache/mojang_<version>.jar`.

## Core implementation constraints

- Module assignment is strict for configurable modules at top-level category scope: every non-internal module must appear in at most one of `always_enabled_modules`, `disabled_modules`, or the aggregate `modesets` category. A module may appear in more than one individual modeset because modesets are alternative player modes.
- Internal modules (`modeset-listener`, `attack-cooldown-tracker`, `entity-damage-listener`) are always enabled and must not be listed in configurable module groups.
- Reload/enable must fail for invalid module assignment rather than silently choosing a fallback.
- Modules are enabled/disabled solely via `always_enabled_modules`, `disabled_modules`, and `modesets`; there is no per-module `enabled:` toggle.
- When adding new integration test specs, add them to the explicit `.withClasses(...)` list in `KotestRunner` because autoscan is disabled.
- New classes should be written in Kotlin by default where practical, matching `.github/CONTRIBUTING.md`.

## Integration test essentials

- Tests run inside a real Paper server started by the Gradle `run-paper` plugin.
- RunServer output is redirected to `build/integration-test-logs/<version>.log`; root and user-facing agents must leave any needed log inspection to subagents after compact summaries and `plugins/OldCombatMechanicsTest/test-failures.txt` prove insufficient.
- Kotlin tests use Kotest 6 for Java 11+ server targets (`KotestRunner`, `KotestProjectConfig`).
- Java 8 targets use `LegacyTestRunner`, currently a smoke-test path rather than the full Kotest suite.
- Several integration tests intentionally use synthetic Bukkit events or direct module handler calls. Consult `integration-test-verification` before assuming a test represents a real in-world action.

## Historical notes index

- Detailed relocated history from the former long `AGENTS.md` lives in `.agents/skills/integration-test-verification/references/relocated-agents-notes.md`.
- Dependabot, dependency bump, changelog, licence, JVM/classfile, GitHub Actions, and validation recommendation reviews: `dependabot-pr-review`.
- GitHub issue triage, `gh issue` investigation, fresh issue shortlisting, reproduction recommendations, and regression coverage notes: `github-issue-investigation`.
- Integration harness, fake-player, PacketEvents, and test-shortcut details: `integration-test-verification` and its references.
- Compatibility, Java 8, reflection, NMS, and version strategy details: `compatibility-strategy`.
- Module configuration, modeset, migration, and option-change details: `module-config-change`.
- Commit preparation, pre-commit hooks, Spotless, staging, validation-before-commit, and conventional commit details: `commit-preparation`.
- Publishing, release workflow, licence, and asset notes: `release-readiness-review`.
- PR summary templates and reviewer-facing change grouping: `pr-draft-summary`.

## TDAID reminders (this repo)

- Plan → Red → Green → Refactor → Validate.
- Red phase: only touch tests.
- Green phase: only touch production code.
- Refactor phase: clean-ups only; keep behaviour unchanged and tests green.
- Validate phase: rerun the narrowest relevant tests and do a human sanity check before declaring done.
