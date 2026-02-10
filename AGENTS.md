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
- `KotestRunner` writes a compact `plugins/OldCombatMechanicsTest/test-failures.txt` file (up to 25 failures) so CI can surface failure reasons without opening the full server log.
- Token hygiene: do **not** open/read `build/integration-test-logs/*.log` unless the user explicitly asks for log inspection; rely on the compact console summary by default and ask for permission before digging into full logs.
- PacketEvents is shaded into the plugin; integration tests do not inject external packet libraries.
- `relocateIntegrationTestClasses` (ShadowJar) relocates PacketEvents references in test classes only.
- `integrationTestJar` is a plain `Jar` task that embeds the relocated test classes plus runtime deps, excluding PacketEvents so the test plugin resolves PacketEvents from the main plugin’s shaded copy.
- `PacketCancellationIntegrationTest` now uses a cancellable `CompletableFuture.await()` so `withTimeout` actually aborts when no packet arrives.
- `PacketCancellationIntegrationTest` now drives PacketEvents via `PacketEventsImplHelper.handleClientBoundPacket` with a synthetic `User` instead of relying on live channel injection; this avoids timeouts when PacketEvents does not emit send events for fake channels.
- `PacketCancellationIntegrationTest` sets the Bukkit player on the synthetic PacketEvents event so module listeners can match `PacketSendEvent#getPlayer`.
- Matrix task: `integrationTest` depends on `integrationTestMatrix` which runs per-version tasks like:
  - `integrationTest1_19_2`, `integrationTest1_21_11`, `integrationTest1_12`, `integrationTest1_9`
- Test result handoff:
  - `OCMTestMain` writes `plugins/OldCombatMechanicsTest/test-results.txt` containing `PASS` or `FAIL`.
  - `KotestRunner` also writes `plugins/OldCombatMechanicsTest/test-failures.txt` (small failure summary).
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
- PacketEvents is now used for packet interception (shaded and relocated in the main jar).
- PacketEvents dependency moved to `2.11.2-SNAPSHOT` (CodeMC snapshots) for 1.21.11 support.
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
- The Hangar publish workflow exports `HANGAR_API_TOKEN` to match the Gradle Hangar publish configuration.
- `ModuleFishingRodVelocity` uses a single shared per-tick task (1.14+) to adjust hook gravity for all active hooks, instead of one scheduled task per hook.
- `AttackCooldownTracker#getLastCooldown` is safe to call when the tracker is not registered (returns null) and uses a `HashMap` rather than a `WeakHashMap`.
- `AttackCooldownTracker` defensively feature-detects `HumanEntity#getAttackCooldown` and avoids scheduling its per-tick sampler when the API exists (modern/backported servers).
- `ModulePlayerKnockback` uses a `HashMap` + a single shared 1-tick expiry cleaner for pending velocity overrides, instead of `WeakHashMap` + one scheduled task per hit.
- `ModuleShieldDamageReduction` uses a `HashMap` + a single shared 1-tick expiry cleaner for the “fully blocked” armour-durability suppression, instead of `WeakHashMap` + one scheduled task per hit.
- `ModuleOldArmourDurability` uses a `HashMap` + a single shared 1-tick expiry cleaner for the “explosion damaged armour” suppression, instead of `WeakHashMap` + one scheduled task per explosion.
- `ModulePlayerRegen` uses tick-based interval tracking (1.8-like; TPS aware) with a `HashMap` and a single shared tick counter task that runs only while players are tracked.
- `ModuleDisableEnderpearlCooldown` uses a `HashMap` and lazily drops expired cooldown entries during checks (wall-clock cooldown; no recurring task).
- `ModuleSwordBlocking` no longer version-gates Paper support; it feature-detects Paper data component APIs and avoids ConcurrentModificationException by iterating legacy tick state over a snapshot.
- Do not gate behaviour on hard-coded Minecraft version numbers; use feature detection (class/method presence) because some servers backport APIs.
- Weapon/armour unknown-enchantment warnings only fire for non-`minecraft` namespaces; legacy servers fall back to a known vanilla-enchantment list to avoid warning on built-ins.
- For NMS access, prefer the project Reflector helpers (`utilities.reflection.Reflector` + `ClassType`) over ad-hoc reflection, and avoid hard-coded versioned class names where heuristics (signatures/fields) can locate methods safely.
- Added integration tests in `OldPotionEffectsIntegrationTest` for strength addend scaling (Strength II and III), a distinct modifier value check, and strength multiplier scaling.
- Added integration test ensuring vanilla strength addend applies when `old-potion-effects` is disabled.
- Strength modifier in `OCMEntityDamageByEntityEvent` now stores per-level value (3) and applies level when reconstructing base damage.
- Added `OldToolDamageMobIntegrationTest` to assert old-tool-damage config affects vindicator iron-axe hits.
- `KotestRunner` class list updated to include `OldToolDamageMobIntegrationTest`.
- `ModuleOldToolDamage` now adjusts mob weapon damage by shifting base damage with the configured-vs-vanilla delta for non-player damagers.
- `ModuleOldToolDamage` documents that mob custom weapons are not detected; the delta is always applied for mobs and may conflict with other plugins.
- Added `WeaponDurabilityIntegrationTest` covering tool durability vs hit counts during invulnerability and after it expires (FakePlayer attacker vs FakePlayer victim); registered in `KotestRunner`.
- `WeaponDurabilityIntegrationTest` writes debug summaries to `build/weapon-durability-debug-<version>.txt`.
- `WeaponDurabilityIntegrationTest` and `OldToolDamageMobIntegrationTest` now resolve debug output paths relative to the repo root (based on the server run directory), avoiding hard-coded home paths.
- `OldToolDamageMobIntegrationTest` uses a Villager victim and waits for a real Vindicator hit by setting a target and retrying with tick delays (plus a best-effort NMS attackCompat call) before asserting the mob tool-damage delta.
- Module assignment is strict for configurable modules: every non-internal module must appear in exactly one of `always_enabled_modules`, `disabled_modules`, or a modeset. Internal modules (`modeset-listener`, `attack-cooldown-tracker`, `entity-damage-listener`) are always enabled and must not be listed; reload/enable fails if they are configured.
- Use British English spelling and phraseology at all times.
- DO NOT use American English spelling or phraseology under any circumstances.
- Never hard-code absolute filesystem paths in tests or production code; resolve locations relative to the repo root or server run directory.
- Added `DisableOffhandIntegrationTest` to assert the disable-offhand modeset-change handler does not clear the offhand when the module is not enabled for the player.
- `KotestRunner` now includes `DisableOffhandIntegrationTest` in its explicit class list.
- When adding new integration test specs, add them to the explicit `.withClasses(...)` list in `KotestRunner` because autoscan is disabled.
- Added `ToolDamageTooltipIntegrationTest` (in `KotestRunner` list) to define behaviour for an opt-in “configured tool damage tooltip” feature (lore line) under `old-tool-damage.tooltip` (`enabled`, `prefix`).
- `old-tool-damage.tooltip.enabled` is now enabled by default in the bundled config so players can see the configured damage in-game.
- Modules are enabled/disabled solely via `always_enabled_modules`, `disabled_modules`, and `modesets` (no per-module `enabled:` toggle).
- `SwordBlockingIntegrationTest` uses synthetic `PlayerInteractEvent` right-clicks; on legacy (offhand-shield) path this cannot reliably assert `isBlocking`/`isHandRaised` (client-driven), so tests treat “blocking applied” as either a shield injection or a raised hand depending on path.
- Added `PacketCancellationIntegrationTest` to cover PacketEvents sweep-particle and attack-sound cancellation using PacketEvents wrappers/listeners (registered in `KotestRunner`).
- Added `ModesetRulesIntegrationTest` to cover always-enabled, disabled, and modeset-scoped module rules plus reload failures for invalid assignments.
- Added `ConfigMigrationIntegrationTest` to cover config upgrade migration into always/disabled module lists and preservation of custom modesets.
- Added a `weakness should not store negative last damage values` test in `InvulnerabilityDamageIntegrationTest` that forces `old-potion-effects.weakness.modifier = -10`, consumes a weakness potion (amplifier -1), attacks once, and asserts the stored last damage is non-negative; passes on 1.12/1.19.2 after clamping, but 1.21.11 can still fail with `No stored last damage for victim (events=0)` (attack event not recorded).
- `EntityDamageByEntityListener.checkOverdamage` now clamps the stored `lastDamages` value to a minimum of 0 to avoid negative last-damage entries when weakness (or other modifiers) drives pre-clamp damage below zero.
- Weakness amplifier clamping changed in 1.20+: attempts to use amplifier `-1` are clamped to `0` (Weakness I). With low-damage weapons this can yield zero vanilla damage, and Paper 1.21 does not fire `EntityDamageByEntityEvent` for zero damage, so tests that rely on an EDBE hit must use a stronger weapon or account for the clamp.
- `OldPotionEffectsIntegrationTest` now disables the module by moving `old-potion-effects` into `disabled_modules` (and removing it from modesets/always lists), then uses `Config.reload()`; the `withConfig` helper restores module lists/modesets and saves+reloads config to keep state consistent.
- `FireAspectOverdamageIntegrationTest` includes afterburn-vs-environmental fire-tick checks for both player and zombie victims, with and without Protection IV armour (mirrors issue 707 MRE).
- Release workflow (`.github/workflows/build-upload-release.yml`) now uploads Bukkit files via `itsmeow/curseforge-upload@v3` with `game_endpoint=bukkit`, reusing `DBO_UPLOAD_API_TOKEN`, and namespaces `GAME_VERSIONS` as `Minecraft <ver>:<ver>` to avoid Bukkit version ambiguity.

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
- FakePlayer uses a plain `EmbeddedChannel` (not an anonymous subclass) so PacketEvents treats it as fake and does not disallow login; dummy `decoder`/`encoder` handlers are still added to the pipeline.
- FakePlayer now schedules a manual NMS tick for non-legacy servers (prefers `doTick`, then `tick`, falls back to `baseTick`) to drive vanilla ticking like fire and passive effects.
- FakePlayer tick shim invokes `baseTick` whenever `remainingFireTicks > 0` (burning) to ensure fire tick damage events still occur on Paper 1.21+ (which can short-circuit `doTick`/`tick` for fake players). When the fake player is in water, it prefers `doTick`/`tick` so the server can properly clear fire ticks (extinguish) before any fire damage is applied.
- FakePlayer now prefers `PlayerList.placeNewPlayer` over the legacy `load`/manual list insertion path to better mirror vanilla login initialisation (helps player fire-tick damage on modern servers).
- FakePlayer does not emulate fire-tick damage; fire ticks should be driven by the NMS tick path.
- FakePlayer now forces a world add when the Bukkit world does not report the fake player entity after `placeNewPlayer` to keep PvP interactions reliable.
- FakePlayer now clears invulnerability/instabuild abilities after spawn (plus a legacy fallback) to improve PvP interactions between fake players.
- EntityDamageByEntityListener now logs extra debug about lastDamage restoration for non-entity damage, and documents the vanilla 1.12 damage flow in checkOverdamage.
- EntityDamageByEntityListener no longer overwrites the stored last-damage baseline when cancelling “fake overdamage” (e.g. cancelled fire tick during invulnerability), preventing subsequent hits from incorrectly bypassing immunity.
- Stored last-damage baselines now use a single lightweight expiry sweeper (tick-based TTL) instead of scheduling one Bukkit task per damage event; this keeps the hot path allocation-free. Expiry is monotonic (only extended, never shortened) and has a small minimum TTL to tolerate `maximumNoDamageTicks = 0`.
- `WeaponDurabilityIntegrationTest` now uses a Zombie victim (fake-player attacker) and prefers the Bukkit `Player#attack` API before falling back to reflective NMS attack resolution, to make hit delivery reliable on modern servers.
- `ModuleSwordSweepParticles` and `ModuleAttackSounds` now use PacketEvents listeners/wrappers instead of ProtocolLib.
- `PacketCancellationIntegrationTest` now builds a `PacketSendEvent` directly (reflection) from the transformed buffer, sets the packet id/type explicitly, and dispatches it via the PacketEvents `eventManager` so module listeners can cancel reliably.
- README now includes a licence note: source remains MPL‑2.0, but pre-built jars bundling PacketEvents are distributed under GPLv3; builds without PacketEvents can remain MPL‑2.0.
- Legacy fake player (1.9) now uses a plain `EmbeddedChannel` with dummy `decoder`/`encoder` handlers, mirroring the modern fake player setup so PacketEvents treats it as fake.
- `ModuleChorusFruit` now reimplements the chorus teleport search (16 attempts, world-border aware, passable feet/head, solid ground) for custom teleport distances; falls back to vanilla target if no safe spot found.
- Added `ChorusFruitIntegrationTest` (in KotestRunner list) to assert custom chorus teleport distance lands on a safe block within the configured radius.
- Chorus fruit safety test now handles legacy 1.12 by using solid/non-solid checks when `Block#isPassable` is absent; passes on 1.12, 1.19.2, and 1.21.11.
- `ModuleOldToolDamage` now supports configurable TRIDENT (melee), TRIDENT_THROWN, and MACE damage; mace preserves its vanilla fall bonus while overriding base damage. New defaults added to `old-tool-damage.damages`.
- Added `ModuleAttackRange` (Paper 1.21.11+ only) to apply a configurable attack_range data component (default 1.8-like: 0–3 range, creative 0–4, margin 0.1, mob factor 1.0); auto-disables on Spigot/older versions. `attack-range` module listed in `disabled_modules` by default.
- `ModuleSwordBlocking` now only strips the Paper `CONSUMABLE` component from sword items, preventing food and other consumables from inheriting a `!minecraft:consumable` patch when inventory events fire on 1.20.5+.
- Added `DisableOffhandReflectionIntegrationTest` (in `KotestRunner` list) to ensure reflective access to `InventoryView#getBottomInventory`/`getTopInventory` works on non-public CraftBukkit view implementations.
- `Reflector.getMethod` overloads now include declared methods and call `setAccessible(true)` to avoid `IllegalAccessException` when CraftBukkit uses non-public view classes (e.g. `CraftContainer$1` on 1.20.1).
- Added `AttackRangeIntegrationTest` (1.21.11+) to assert vanilla hits at ~3.6 blocks and 1.8-style attack_range reduces reach so the same hit misses; registered in `KotestRunner`.
- InvulnerabilityDamageIntegrationTest adds a case asserting environmental damage above the baseline applies during invulnerability (manual EntityDamageEvent).
- `gradle.properties` gameVersions list now includes 1.21.11 down to 1.21.1 (plus 1.21) ahead of existing entries.
- GitHub release asset now keeps a stable filename `OldCombatMechanics.jar` (no version suffix); the CurseForge upload uses the same path.
- Expanded `SwordBlockingIntegrationTest` to cover right-click blocking, non-sword handling, offhand restoration (hotbar change and drop cancel), permission gating, and preserving an existing real shield.
- Added `PaperSwordBlockingDamageReductionIntegrationTest` (in KotestRunner list) to regression-test that Paper sword blocking is recognised server-side (via `ModuleSwordBlocking.isPaperSwordBlocking`) and produces a non-zero 1.8-style reduction from `applyPaperBlockingReduction`. This uses a synthetic damage event to avoid flakiness from mob AI / PvP settings.
- Paper-only sword blocking uses a runtime-gated helper (`kernitus.plugin.OldCombatMechanics.paper.PaperSwordBlocking`) that applies consumable + blocking components via cached reflection (lookup once, MethodHandle invoke thereafter). 1.8-style reduction is applied via `EntityDamageByEntityListener` using the `BLOCKING` damage modifier, while legacy/non-Paper keeps the shield swap path.
- Fixed legacy (1.9.x) sweep detection flakiness by tracking priming per-attacker UUID (not `Location`, which is unstable due to yaw/pitch) and clearing the set on module reload; stabilises `SwordSweepIntegrationTest` on 1.9.4.
- `ModuleSwordSweep` now uses a one-shot next-tick clear (single scheduled task guarded by `pendingClearTask`) instead of a repeating every-tick task, to avoid doing any work on ticks where no sword hits occurred.
- `ModuleSwordBlocking` legacy (offhand shield) path no longer schedules per-player repeating tasks; it now uses a single shared tick runner with per-player tick deadlines (10-tick warmup + 2-tick poll + restoreDelay), which reduces task churn under heavy right-click spam.
- `ModuleLoader` now clears the static module list on initialise to prevent duplicate registrations after hot reloads.
- Added `ConsumableComponentIntegrationTest` coverage to assert the Paper sword-blocking consumable cleaner leaves swords untouched when the module is disabled (modeset/world) and does not mutate swords when no component change is required.
- `ConsumableComponentIntegrationTest` now seeds and asserts the CONSUMABLE component via NMS reflection (not Paper API) and uses standalone CraftItemStacks for cursor/current items to avoid classloader mismatches and fake-player cursor side effects.
- `ModuleSwordBlocking#onModesetChange` now strips the Paper CONSUMABLE component from the player’s main hand/offhand and stored swords when sword-blocking is disabled for that player, preventing component “taint” lingering after mode changes.
- `ModuleSwordBlocking#reload` now strips Paper sword-blocking consumable components from online players when the module is disabled globally (disabled_modules) to avoid lingering taint after config reloads.
- `ModuleSwordBlocking` now gates the Paper consumable animation by PacketEvents client version (>=1.20.5); older clients fall back to the offhand shield and unknown versions default to the animation path.
- `config.yml` now documents that Paper 1.20.5+ uses the consumable-based sword-blocking animation, with older/Paperless servers falling back to an offhand shield.
- `config.yml` now notes that ViaVersion clients older than 1.20.5 also fall back to the shield behaviour.
- Added `ConsumableComponentIntegrationTest` coverage for disabling sword-blocking via `disabled_modules` and asserting the consumable component is cleared after config reload.
- Extended `ConsumableComponentIntegrationTest` to cover disabled-module right-click suppression, reload toggling, stored-inventory cleanup, offhand stability, and modeset-change behaviour after a disabled reload.
- Added `ConsumableComponentIntegrationTest` coverage for forcing an older client version and asserting sword-blocking falls back to an offhand shield without applying consumable components.
- `ConsumableComponentIntegrationTest` now uses PacketEvents reflection to seed a User/client version for fake players when PacketEvents has not registered one yet.
- `ModuleSwordBlocking#restore` no longer skips offhand restoration just because Paper support is present; older clients using the shield fallback now restore their original offhand item correctly.
- Added `ConsumableComponentIntegrationTest` coverage asserting the older-client shield fallback restores the offhand item on hotbar change.
- Added `ConsumableComponentIntegrationTest` coverage for inventory-glitch regressions: unknown PacketEvents client versions should use shield fallback, middle-clicking custom GUIs should not mutate held sword components, and custom-GUI drag events should not rewrite top-inventory swords.
- Those inventory-glitch regressions now pass on 1.21.11 after the unknown-client fallback + click/drag scope fixes.
- `ModuleSwordBlocking#supportsPaperAnimation` now treats unknown PacketEvents client versions as shield-fallback only when a PacketEvents `User` exists; if no user is registered yet (early login/synthetic test player), it keeps animation support to avoid regressing normal modern-client behaviour.
- `ModuleSwordBlocking.ConsumableCleaner#onInventoryClickPost` now re-applies the main-hand consumable component only for click paths that can actually affect the selected hotbar slot (selected NUMBER_KEY swaps or direct selected-slot player-inventory clicks), and ignores middle-clicks.
- `ModuleSwordBlocking.ConsumableCleaner#onInventoryDrag` now ignores top-inventory raw slots and skips main-hand re-application when the drag only touched top inventory slots, preventing custom-GUI slot rewrites.
- Added `ConsumableComponentIntegrationTest` coverage for legacy fallback shield-guard scope: custom GUI shield-icon clicks and unrelated shield drops should not be cancelled while fallback is active; temporary offhand shield swap blocking should remain enforced.
- `ModuleSwordBlocking#onInventoryClick` legacy shield-guard scope now only cancels direct offhand temporary-shield interactions (player inventory slot 40), avoiding cancellation of unrelated custom-GUI shield clicks.
- `ModuleSwordBlocking#onInventoryClick` also blocks `ClickType.SWAP_OFFHAND` while temporary legacy shield state is active, preventing offhand shield extraction via inventory swap-clicks.
- `ModuleSwordBlocking#onItemDrop` no longer cancels shield drops while legacy fallback state is active; it now force-restores the temporary shield state immediately to avoid trapping unrelated shield drops.
- `ModuleSwordBlocking#isPlayerBlocking` now requires an actual offhand shield before treating `isBlocking`/`isHandRaised` as legacy shield-blocking, preventing stale hand-use state from suppressing fallback shield injection.
- `ModuleSwordBlocking#supportsPaperAnimation` now falls back to `User#getClientVersion` when `PlayerManager#getClientVersion` is null, improving old-client fallback stability in synthetic/integration scenarios.
- The new legacy-scope regressions now pass on 1.19.2 and 1.21.11.

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
