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
- A single-version integration run can use `-PintegrationTestServerJar=build/test-servers/<server>.jar` to exercise a local Paper-compatible server such as Purpur.
- Matrix task examples: `integrationTest1_19_2`, `integrationTest1_21_11`, `integrationTest1_12`, `integrationTest1_9`.
- Java toolchain override example: `ORG_GRADLE_JAVA_INSTALLATIONS_PATHS=/path/to/jdk8:/path/to/jdk17:/path/to/jdk25 ./gradlew integrationTest`.
- `checkTestResults<version>` fails the build if `plugins/OldCombatMechanicsTest/test-results.txt` is missing or not `PASS`.
- Multiple `kotest.filter.specs` alternatives must use a single regex, for example `*(First|Second)IntegrationTest`; comma-separated filters intersect in the active Kotest runner and can select zero tests. Require parsed test counts before accepting a filtered run.
- `KotestRunner` writes a compact `plugins/OldCombatMechanicsTest/test-failures.txt` summary for CI-friendly failure triage.

## Skill selection guide

- **Mandatory trigger:** Use `dependabot-pr-review` for Dependabot PRs, dependency bumps, Gradle/Maven dependency updates, GitHub Actions version updates, dependency changelog/licence/release-note review, and dependency validation recommendations. Other skills remain relevant for deeper integration-test selection (`integration-test-verification`), compatibility strategy (`compatibility-strategy`), and release-readiness concerns (`release-readiness-review`).
- **Mandatory trigger:** Use `github-issue-investigation` for GitHub issue triage, `gh issue` investigation, picking a fresh issue, reproducing issue reports, or identifying durable regression coverage. Do not use it for GitHub-side mutation or implementation once the investigation is complete.
- **Mandatory trigger:** Use `integration-test-verification` for integration-test runs, matrix selection, compact failure triage, Kotlin integration test authoring, or `KotestRunner` registration questions. Do not use it for ordinary unit-free source edits with no test-harness impact.
- **Mandatory trigger:** Use `compatibility-strategy` for Java 8 compatibility, Bukkit/Paper version differences, NMS/reflection, fake players, PacketEvents compatibility, feature detection, or legacy-server fallbacks. Do not use it for release notes or configuration-only routing.
- **Mandatory trigger:** Use `module-config-change` for `config.yml`, module enablement, modesets, migrations, configurable module assignment, or per-module settings such as tool damage, attack cooldown, attack range, or sword blocking options. Do not use it for pure test execution or release publishing.
- **Mandatory trigger:** Use `commit-preparation` for preparing commits, pre-commit hooks, Spotless, formatting or linting, validation before commit, staging files, and conventional commit messages. Do not use it for implementation design or PR prose unless commit readiness is also in scope.
- **Mandatory trigger:** Use `release-readiness-review` for GitHub release, Hangar, CurseForge/BukkitDev, licence, workflow, supported-version, or release asset checks. Do not use it for feature implementation unless the change affects release packaging.
- **Mandatory trigger:** Use `user-facing-changelog` for rewriting `CHANGELOG.md`, GitHub release notes, or Release Please PR changelog sections into user-facing release notes. This is separate from `release-readiness-review`, which is for publishing, assets, licence, supported-version, and workflow readiness checks.
- **Mandatory trigger:** Use `pr-draft-summary` when drafting PR descriptions, change summaries, reviewer notes, or risk/test sections. Do not use it while still deciding implementation strategy.

## Current project state

- Legacy fake players have dedicated 1.9 and 1.12 implementations; use focused native-flight validation when changing legacy projectiles.
- Java 8 compatibility is required for main code. Avoid APIs such as records, pattern matching, `Stream.toList()`, and `Set.of`/`List.of` in Java 8-targeted code.
- Main Java/Kotlin compilation targets Java 8 (`options.release.set(8)`, Kotlin `jvmTarget = 1.8`).
- Pre-1.13 integration-test versions use `integrationTestJavaVersionLegacyPre13` (default 8); modern versions use Java 25 for `>=1.20.5` and Java 17 otherwise.
- Legacy vanilla jars for `<=1.12` are downloaded by `downloadVanilla<version>` into `run/<version>/cache/mojang_<version>.jar`.

## Core implementation constraints

- `old-potion-throwing` independently restores player splash-potion launch geometry and Gaussian spread; lingering potions, witches and dispensers retain native launches. Position offsets use legacy Paper post-insertion events or detected modern section insertion before launch completes, avoiding chunk-boundary removal and section-update movement guards. Unsupported servers use a next-tick relative offset. Custom gravity uses one age-aware task and keeps native drag and tick order; the first native tick remains unchanged.
- `fishing-rod-velocity.gravity` defaults to 0.04. The legacy 1.8 and 1.9 hook uses 0.04; the rewritten hook (already present in 1.12) uses 0.03, detected through its enum state machine. Custom gravity preserves native water handling.
- `OldPotionThrowingIntegrationTest` and `FishingGravityIntegrationTest` invoke real native item use and record consecutive native flight positions. Their shared helper does not synthesise launch events or advance entity ticks manually.
- `old-player-knockback.knockback-friction` divides existing velocity on all three axes once per hit before adding knockback. Reload validates a positive finite number, falling back to 2.0 for missing or invalid values (#847).
- `projectile-knockback` restores knockback through small damage values; full damage resistance can prevent it, as documented in the bundled config comments.
- Native critical damage uses Purpur's runtime per-world critical multiplier when its optional configuration API exists, including after Purpur reload. Non-positive or non-finite multipliers leave incoming damage unchanged because they cannot be safely reversed (#833).
- `DamageTypeTags` reads live Bukkit damage-type tags through cached API accessors, with cause-based fallback when the API or tag is unavailable. Armour, Resistance, enchantments and shield projectile settings use the original source; vanilla `in_fire` retains legacy armour protection. Shield settings still require native blocking.
- Defence Resistance reads use `PotionEffects` so Bukkit 1.9 can fall back to active effect enumeration. `ModuleInteractionEdgeCasesIntegrationTest` uses a test-only native absorption setter fallback on APIs predating Bukkit absorption accessors.
- Defence recalculation must replace the `MAGIC` damage modifier even when remaining damage is zero, so stale legacy resistance adjustments cannot cause excess damage (#621).
- Legacy attack cooldown tracking marks player `ENTITY_ATTACK` damage at `MONITOR` when native recharge decreased, including cancelled native hits, and refreshes its native sample when the next primary attack enters damage recalculation in the same tick. Constructed damage that leaves native recharge unchanged does not consume the sample. Reads during the event and recognised secondary sword sweep hits retain the original sample. Attacks suppressed before Bukkit emits damage and attacks nested inside another damage event remain limitations of the legacy per-tick fallback. Servers without `ENTITY_SWEEP_ATTACK`, including Bukkit 1.9, retain the original per-tick fallback because secondary sweep hits share the primary attack cause.
- Recognised native sword sweeps retain the server's incoming offensive damage. Tool, potion and critical handlers leave their neutral offensive components unchanged; the common pipeline still dispatches the custom OCM event and processes defence, shielding, immunity and overdamage. Custom base edits apply; custom-event cancellation retains its existing behaviour of leaving the original Bukkit damage unchanged. Native Sweeping Edge calculations retain the server's weapon basis even when OCM changes the primary hit. The sweep branch in `OCMEntityDamageByEntityEvent` documents this limitation and the weapon-attribute follow-up for #890; potion effects need separate consideration. Servers without a distinct sweep damage cause retain the previous fallback.

- Module assignment is strict for configurable modules at top-level category scope: every non-internal module must appear in at most one of `always_enabled_modules`, `disabled_modules`, or the aggregate `modesets` category. A module may appear in more than one individual modeset because modesets are alternative player modes.
- Internal modules (`modeset-listener`, `attack-cooldown-tracker`, `entity-damage-listener`) are always enabled and must not be listed in configurable module groups.
- bStats `enabled_modules` reports servers enabling each configurable module. `enabled_modules_count` (pie) and `enabled_modules_count_bar` (bar) report the distribution of enabled configurable module counts per server. All three exclude internal modules; the count charts include zero.
- Reload/enable must fail for invalid module assignment rather than silently choosing a fallback.
- Modules are enabled/disabled solely via `always_enabled_modules`, `disabled_modules`, and `modesets`; there is no per-module `enabled:` toggle.
- When adding new integration test specs, add them to the explicit `.withClasses(...)` list in `KotestRunner` because autoscan is disabled.
- New classes should be written in Kotlin by default where practical, matching `.github/CONTRIBUTING.md`.

## Integration test essentials

- Native sweep coverage separately checks equal-hit immunity, fully and partially consumed absorption, and increasing Sweeping Edge damage followed by a weaker hit. Increasing-overdamage snapshots record incoming damage, restored damage history, immunity ticks, applied Bukkit damage and actual health. Configurable damage modules are disabled for independent native controls; these can suppress a weaker hit before Bukkit emits an event, while the enabled OCM pipeline emits and cancels that hit.
- Sweep absorption fixtures provision the optional native `MAX_ABSORPTION` attribute. History snapshots detect the optional `INVULNERABILITY_REDUCTION` modifier because newer native events retain full Bukkit BASE damage and expose immunity reduction separately. Ordinary legacy sweeps identify the secondary victim even when the native cause is shared with primary attacks. Sweeping Edge cases and Bukkit persistent-data cases use explicit Kotest capability skips only when their underlying feature is absent; `weapon-baseline-capabilities.txt` records the reasons.

- `WeaponDamageBaselineIntegrationTest` uses one native player attack per attempt, including suppressed hits. It checks configured weapon families, custom attributes, implicit native attack speed, partial cooldown, reload and modeset transitions, native drop and pickup transfers, foreign metadata, immunity, cancellation and foreign event adjustments. Its hotbar notifications are synthetic; native entity ticking applies held-item attributes. `WeaponBaselineNativeCompat` supplies native item-modifier creation and inspection, dropping and shield activation when the corresponding Bukkit API is absent. Modifier identity, operation, amount and slot remain checked, including unrelated metadata and native transfer. Shield fixtures restore their solid ground instead of requiring a gravity API. The retained Weakness regression records raw damage 3.0 and final damage 0.625 for configured sword damage 4.625 on Paper 1.19.2 and 1.21.11. The partial-cooldown case compares consecutive attacks with the disabled control, including a cancelled first attack. The 1.9.4 disabled native diamond-sword control now deals 7.0 after native connection ticking restored held-item attributes. Its partial-cooldown case still fails because the original per-tick fallback remains active. Keep genuine numerical failures visible rather than skipping or weakening them.

- `LegacyFakePlayer12` cancels its scheduler-driven player tick before removal and clears adapter references afterwards. Native transfer cases require the pickup event to identify the intended recipient, preventing removed fake players from silently collecting later fixtures' items.

- Native 1.9 sweep controls deal 1.0 with Strength or Weakness. The retained shared-cause reconstruction currently deals 0.0 with default Strength, 2.0 with additive Strength and 4.5 with Weakness. These numerical regressions remain visible alongside the known same-tick cooldown failure; fixture compatibility does not redefine their expected damage.

- Complete weapon-baseline and critical validation runs contain 83 cases: Paper 1.19.2 and 1.21.11 each pass all 83; Paper 1.12 passes 82 with one absent-PDC skip; Paper 1.9.4 passes 72, fails the four numerical regressions above, and skips six absent-Sweeping-Edge cases plus one absent-PDC case. Legacy runs use Java 17 and the patched server jar. Capability skips are distinct from tests omitted by a focus filter.

- Native Weakness controls on Paper 1.19.2 and 1.21.11 deal 3.0 with configurable modules disabled or with only `old-critical-hits` enabled. Base damage reconstruction subtracts the negative native Weakness modifier before configured effects are applied, recovering 7.0 from raw damage 3.0. With compensation present, native Weakness contributes zero to reconstruction. The legacy potion control deals 4.125 on both versions, covering compensation on 1.21.11 and native reversal on 1.19.2. The four native Weakness cases also pass on Paper 1.12 and 1.9.4 with the Java 17 test harness and patched server jar. Their diagnostics use `PotionEffects` for APIs without `Player.getPotionEffect`. Compact snapshots live in `plugins/OldCombatMechanicsTest/weapon-weakness-diagnostics.txt`.

- `OldCriticalHitsIntegrationTest` spawns its native target after the recharge wait so legacy cows with native gravity cannot fall before the attack. The former Paper 1.19.2 missing-event failure was a fixture error: its cow fell from y=100 to y=69 and died before the attack. Legacy equipment leaves player base damage unchanged and uses native held-item modifiers. The general hit helper attempts native attacks before its compatibility damage fallback. The native server-multiplier case invokes one attack, checks target validity and full recharge, and records compact state in `plugins/OldCombatMechanicsTest/critical-native-diagnostics.txt`.
- The `critical hit multiplies configured iron axe damage` regression uses one native attack per hit without sprinting, checks incoming native damage, and retains strict final damage expectations of 4.5 and 5.625 for the configured 1.25 multiplier. Dedicated neighbouring cases retain critical-hit coverage while sprinting.

- `PreAttackProbeIntegrationTest` is an opt-in Paper experiment (select its full spec name with `kotest.filter.specs`) using native `Player.attack` exactly once per attempt. It checks preattack attribute changes before immunity and demonstrates that restoring attributes only in damage events misses cancelled or suppressed attacks.

- The issue #864 cases in `InvulnerabilityDamageIntegrationTest` use native command damage on diamond-armoured fake players every two ticks. They require `old-armour-strength` to reduce armour-protected custom damage from 1.5 to 0.3 using the original damage source. The isolated critical-hit cases retain native mitigation.

- The `damage tags` cases in `InvulnerabilityDamageIntegrationTest` use native command damage and test-only custom damage types to check protection enchantments, Resistance bypass tags, recognised explosion armour bypass, shield routing, and explosion armour wear. Numerical cases disable legacy enchantment randomness. Each case records health loss, event modifiers and armour wear in `plugins/OldCombatMechanicsTest/damage-tag-results.txt`; records are appended across runs. Shield cases use a real native shield raised by `FakePlayer.doBlocking`, whose helper sets the native use counter directly, and assert that native blocking occurred.

- `ModuleInteractionEdgeCasesIntegrationTest` covers shield defence recalculation, damage listener ordering after reload, cancelled-hit immunity history, cache expiry after task restarts, fishing cancellation, and preservation of other plugins' exhaustion changes. Constructed events are identified in the spec; `PlayerRegenIntegrationTest` also exercises natural regeneration.

- `LegacyFakePlayer9` must only insert a player entity if the real join pipeline has not already made it valid; duplicate insertion removes the joined player through UUID collision handling. Its network manager registers in the native connection collection so the network tick applies held-item attributes and effects. Removal unregisters the manager and releases its embedded channel; Netty 4.0 uses a finish-and-drain fallback. Keepalive responses preserve the legacy integer token.
- `VersionedFakePlayer` uses the detected Paper join finaliser for versioned NMS servers such as 1.16.5. Its synthetic connection ticks in the native network phase and must be removed during cleanup; scheduler-driven player ticks can run before delayed plugin corrections.
- Tests targeting entities through server commands must keep the target chunk loaded, restore its previous state afterwards, and verify the command took effect. A successful `dispatchCommand` return does not prove the target was found.
- Tests run inside a real Paper server started by the Gradle `run-paper` plugin.
- RunServer output is redirected to `build/integration-test-logs/<version>.log`; root and user-facing agents must leave any needed log inspection to subagents after compact summaries and `plugins/OldCombatMechanicsTest/test-failures.txt` prove insufficient.
- Kotlin tests use Kotest 6 for Java 11+ server targets (`KotestRunner`, `KotestProjectConfig`).
- The harness currently always invokes Kotest. Pre-1.13 runs need `-PintegrationTestJavaVersionLegacyPre13=17` for Kotest compatibility and `-PintegrationTestServerJar=run/<version>/cache/patched_<version>.jar` to bypass the Java 8-only Paperclip bootstrap.
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
- User-facing `CHANGELOG.md`, GitHub release-note, and Release Please changelog rewrites: `user-facing-changelog`.
- PR summary templates and reviewer-facing change grouping: `pr-draft-summary`.

## TDAID reminders (this repo)

- Plan → Red → Green → Refactor → Validate.
- Red phase: only touch tests.
- Green phase: only touch production code.
- Refactor phase: clean-ups only; keep behaviour unchanged and tests green.
- Validate phase: rerun the narrowest relevant tests and do a human sanity check before declaring done.
