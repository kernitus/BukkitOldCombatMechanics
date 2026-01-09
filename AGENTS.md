# AGENTS.md

**Prime directive:** If you are reading this file, it is your responsibility as an agent to keep it up to date with any changes you make in this repository.

This file captures repo-specific context discovered while working on this branch.

## Repo overview
- Project: OldCombatMechanics (Bukkit/Paper plugin)
- Branch context: working from `kotlin-tests` branch
- Build tool: Gradle (wrapper currently at 9.2.1)
- JDKs used locally: 8, 11, 17, 25

## Integration test harness (Kotlin)
- Integration tests live in `src/integrationTest/kotlin` and are packaged into `OldCombatMechanics-<version>-tests.jar`.
- Entrypoint plugin class: `kernitus.plugin.OldCombatMechanics.OCMTestMain`.
- Tests run inside a real Paper server started by the Gradle `run-paper` plugin.
- RunServer output is redirected to `build/integration-test-logs/<version>.log`; `checkTestResults<version>` prints only a compact summary/failures to the console.
- Token hygiene: do **not** open/read `build/integration-test-logs/*.log` unless the user explicitly asks for log inspection; rely on the compact console summary by default and ask for permission before digging into full logs.
- Matrix task: `integrationTest` depends on `integrationTestMatrix` which runs per-version tasks like:
  - `integrationTest1_19_2`, `integrationTest1_21_11`, `integrationTest1_12`, `integrationTest1_9`
- Test result handoff:
  - `OCMTestMain` writes `plugins/OldCombatMechanicsTest/test-results.txt` containing `PASS` or `FAIL`.
  - Gradle `checkTestResults<version>` fails build if file missing, or content is not `PASS`.

## Version matrix + Java selection
- Config is in `build.gradle.kts`:
  - `integrationTestVersions` from property or defaults.
  - `requiredJavaVersion` selects Java based on version.
  - Pre-1.13 versions use `integrationTestJavaVersionLegacyPre13` (default 8).
  - Modern versions use Java 25 if `>=1.20.5` else Java 17.
- Legacy vanilla jar cache:
  - `downloadVanilla<version>` task downloads Mojang server jars for <=1.12 and writes `run/<version>/cache/mojang_<version>.jar`.

## Kotlin test runner split (Java 11+ vs Java 8)
- Kotest 6 (Java 11+) is used for 1.19.2 and 1.21.11.
  - Kotest runner class: `kernitus.plugin.OldCombatMechanics.KotestRunner`
  - Project config: `KotestProjectConfig`
- Java 8 uses a separate runner:
  - `kernitus.plugin.OldCombatMechanics.LegacyTestRunner`
  - Currently a **smoke test** only (verifies plugin enabled + `WeaponDamages` loaded).
  - This is a placeholder and **not a full integration test**.

## Fake player implementation notes
- Primary fake player implementation is `src/integrationTest/kotlin/kernitus/plugin/OldCombatMechanics/FakePlayer.kt`.
- It relies on `xyz.jpenilla:reflection-remapper` to map modern NMS names.
- On legacy servers (1.12), reflection remapper mappings are unavailable; code falls back to `ReflectionRemapper.noop()`.
- As-is, `FakePlayer` uses modern NMS class names (`net.minecraft.server.MinecraftServer`, etc.).
  - This fails on 1.12 which uses versioned NMS (`net.minecraft.server.v1_12_R1.*`).
  - 1.12 requires a dedicated fake player path or version-aware class mapping.

## Java 8 compatibility work
- Java 8 compatibility backports already done in main code (records/pattern matching removed).
- Java 8-incompatible APIs replaced:
  - `Stream.toList()` -> `collect(Collectors.toList())`
  - `Set.of`/`List.of` -> `Collections.unmodifiableSet(new HashSet<>(Arrays.asList(...)))` etc.
- Added missing import in `PotionTypeCompat` for `PotionData`.
- Build config sets `options.release.set(8)` for Java, Kotlin `jvmTarget = 1.8`.

## Dependency / build updates
- Kotlin: 2.3.0
- Kotest: 6.0.7
- run-paper plugin: 3.0.2
- Hangar publish plugin: 0.1.4
- Other deps updated (bstats, netty, BSON, XSeries, authlib, reflection-remapper, adventure).
- JSR-305 added for `javax.annotation.Nullable` (compileOnly).

## Current failing area
- Paper 1.12 integration tests **do not run real fake player tests yet**.
- Desired fix: add proper 1.12 fake player implementation (e.g., version-specific NMS path like `v1_12_R1`).
- Example 1.12 fake player implementation provided by user (NMS, PlayerList manipulation, packet send, etc.).
- We should integrate a conditional path in `FakePlayer` for 1.12 using versioned NMS classes or an alternate helper.

## Local test commands
- Run full matrix:
  - `./gradlew integrationTest`
- Change matrix:
  - `./gradlew integrationTest -PintegrationTestVersions=1.19.2,1.21.11,1.12`
- Set Java toolchain paths (example):
  - `ORG_GRADLE_JAVA_INSTALLATIONS_PATHS=/path/to/jdk8:/path/to/jdk17:/path/to/jdk25 ./gradlew integrationTest`

## Notes
- Paper 1.12 sometimes fails to download legacy vanilla jar from old Mojang endpoint. The custom `downloadVanilla` task fixes that by using the v2 manifest.
- 1.21.11 servers log hostname warnings and Unsafe warnings; tests still pass.
- 1.9 integration tests are currently on hold per user request.
- Kotest filters (`kotest.filter.specs`, `kotest.filter.tests`) are now passed through Gradle into the run-paper JVM args for integration tests.
- Reflection should be used only as a fallback (performance cost); prefer direct API/code paths when available.
- Do not gate behaviour on hard-coded Minecraft version numbers; use feature detection (class/method presence) because some servers backport APIs.
- For NMS access, prefer the project Reflector helpers (`utilities.reflection.Reflector` + `ClassType`) over ad-hoc reflection, and avoid hard-coded versioned class names where heuristics (signatures/fields) can locate methods safely.
- Added integration tests in `OldPotionEffectsIntegrationTest` for strength addend scaling (Strength II and III), a distinct modifier value check, and strength multiplier scaling.
- Added integration test ensuring vanilla strength addend applies when `old-potion-effects` is disabled.
- Strength modifier in `OCMEntityDamageByEntityEvent` now stores per-level value (3) and applies level when reconstructing base damage.
- Module assignment is strict for configurable modules: every non-internal module must appear in exactly one of `always_enabled_modules`, `disabled_modules`, or a modeset. Internal modules (`modeset-listener`, `attack-cooldown-tracker`, `entity-damage-listener`) are always enabled and must not be listed; reload/enable fails if they are configured.
- Use British English spelling and phraseology at all times.
- DO NOT use American English spelling or phraseology under any circumstances.
- Added `DisableOffhandIntegrationTest` to assert the disable-offhand modeset-change handler does not clear the offhand when the module is not enabled for the player.
- `KotestRunner` now includes `DisableOffhandIntegrationTest` in its explicit class list.
- When adding new integration test specs, add them to the explicit `.withClasses(...)` list in `KotestRunner` because autoscan is disabled.
- Added `ModesetRulesIntegrationTest` to cover always-enabled, disabled, and modeset-scoped module rules plus reload failures for invalid assignments.
- Added `ConfigMigrationIntegrationTest` to cover config upgrade migration into always/disabled module lists and preservation of custom modesets.
- Added a `weakness should not store negative last damage values` test in `InvulnerabilityDamageIntegrationTest` that forces `old-potion-effects.weakness.modifier = -10`, consumes a weakness potion (amplifier -1), attacks once, and asserts the stored last damage is non-negative; passes on 1.12/1.19.2 after clamping, but 1.21.11 can still fail with `No stored last damage for victim (events=0)` (attack event not recorded).
- `EntityDamageByEntityListener.checkOverdamage` now clamps the stored `lastDamages` value to a minimum of 0 to avoid negative last-damage entries when weakness (or other modifiers) drives pre-clamp damage below zero.
- Weakness amplifier clamping changed in 1.20+: attempts to use amplifier `-1` are clamped to `0` (Weakness I). With low-damage weapons this can yield zero vanilla damage, and Paper 1.21 does not fire `EntityDamageByEntityEvent` for zero damage, so tests that rely on an EDBE hit must use a stronger weapon or account for the clamp.
- `OldPotionEffectsIntegrationTest` now disables the module by moving `old-potion-effects` into `disabled_modules` (and removing it from modesets/always lists), then uses `Config.reload()`; the `withConfig` helper restores module lists/modesets and saves+reloads config to keep state consistent.
- `FireAspectOverdamageIntegrationTest` includes afterburn-vs-environmental fire-tick checks for both player and zombie victims, with and without Protection IV armour (mirrors issue 707 MRE).

## Test harness shortcuts (known non-realistic paths)
- Several integration tests manually construct and fire Bukkit events rather than triggering real in-world actions:
  - `GoldenAppleIntegrationTest` (manual `PlayerItemConsumeEvent` and `PrepareItemCraftEvent`)
  - `OldPotionEffectsIntegrationTest` (manual `PlayerItemConsumeEvent`, `PlayerInteractEvent`, `BlockDispenseEvent`)
  - `OldArmourDurabilityIntegrationTest` (manual `PlayerItemDamageEvent`, `EntityDamageEvent`)
  - `PlayerKnockbackIntegrationTest` (manual `EntityDamageByEntityEvent`, `PlayerVelocityEvent`)
  - `SwordBlockingIntegrationTest` (manual `PlayerInteractEvent`)
  - `SwordSweepIntegrationTest` (manual `EntityDamageByEntityEvent`)
- Some tests directly invoke module handlers instead of going through the event bus:
  - `PlayerKnockbackIntegrationTest` (direct `module.onEntityDamageEntity`)
  - `SwordSweepIntegrationTest` (direct `module.onEntityDamaged`)
- `AttributeModifierCompat` synthesises a fallback attack-damage modifier from `NewWeaponDamage` when API attributes are missing.
- Fake player implementations use simulated login/network plumbing (EmbeddedChannel + manual login/join/quit events), not a real networked client.
- FakePlayer now schedules a manual NMS tick for non-legacy servers (prefers `doTick`, then `tick`, falls back to `baseTick`) to drive vanilla ticking like fire and passive effects.
- FakePlayer tick shim invokes `baseTick` whenever `remainingFireTicks > 0` (burning) to ensure fire tick damage events still occur on Paper 1.21+ (which can short-circuit `doTick`/`tick` for fake players). When the fake player is in water, it prefers `doTick`/`tick` so the server can properly clear fire ticks (extinguish) before any fire damage is applied.
- FakePlayer now prefers `PlayerList.placeNewPlayer` over the legacy `load`/manual list insertion path to better mirror vanilla login initialisation (helps player fire-tick damage on modern servers).
- FakePlayer does not emulate fire-tick damage; fire ticks should be driven by the NMS tick path.
- EntityDamageByEntityListener now logs extra debug about lastDamage restoration for non-entity damage, and documents the vanilla 1.12 damage flow in checkOverdamage.
- EntityDamageByEntityListener no longer overwrites the stored last-damage baseline when cancelling “fake overdamage” (e.g. cancelled fire tick during invulnerability), preventing subsequent hits from incorrectly bypassing immunity.
- Stored last-damage baselines now use a single lightweight expiry sweeper (tick-based TTL) instead of scheduling one Bukkit task per damage event; this keeps the hot path allocation-free. Expiry is monotonic (only extended, never shortened) and has a small minimum TTL to tolerate `maximumNoDamageTicks = 0`.
- InvulnerabilityDamageIntegrationTest adds a case asserting environmental damage above the baseline applies during invulnerability (manual EntityDamageEvent).

## Fire aspect / fire tick test notes
- `FireAspectOverdamageIntegrationTest` now uses a Zombie victim for real fire tick sampling, with max health boosted (via MAX_HEALTH attribute) to survive rapid clicking.
- The first two tests fire a synthetic `EntityDamageEvent` with `FIRE_TICK` to control timing and make the baseline check deterministic.
- Paper 1.12 applies attack-cooldown scaling before the Bukkit damage event fires; fake players can start with a low initial cooldown, producing a weak first hit and allowing a stronger second hit as legitimate “overdamage”. `FireAspectOverdamageIntegrationTest` now waits a few ticks before the first attack to make the first hit stable on 1.12.
- Added extra fire edge-case coverage in `FireAspectOverdamageIntegrationTest`: fire resistance + fire-immune victim rapid-click parity, water extinguish behaviour (no fire tick damage while submerged), fire protection vs protection comparisons (uses a Zombie victim for consistency), and alternating attackers during invulnerability.
- Legacy (1.12) fake player behaviour differs for player-specific fire tick sampling, so the player afterburn-vs-environmental comparisons are no-ops on legacy; the Zombie variants still run across all versions.

## TDAID reminders (this repo)
- Plan → Red → Green → Refactor → Validate.
- Red phase: only touch tests. Do not modify production code.
- Green phase: only touch production code. Do not modify tests.
- Refactor phase: cleanups only; keep behavior unchanged and tests green.
- Validate phase: rerun tests and do a human sanity check before declaring done.
