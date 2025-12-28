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
- Use British English spelling and phraseology at all times.
- DO NOT use American English spelling or phraseology under any circumstances.

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

## TDAID reminders (this repo)
- Plan → Red → Green → Refactor → Validate.
- Red phase: only touch tests. Do not modify production code.
- Green phase: only touch production code. Do not modify tests.
- Refactor phase: cleanups only; keep behavior unchanged and tests green.
- Validate phase: rerun tests and do a human sanity check before declaring done.
