# Relocated AGENTS.md Historical Notes

This reference preserves detailed repository context that previously made the root `AGENTS.md` difficult to scan. Keep always-on routing rules in `AGENTS.md`; keep operational detail here or in a more specific skill.

## Integration test harness

- Integration tests live in `src/integrationTest/kotlin` and are packaged into `OldCombatMechanics-<version>-tests.jar`.
- Entrypoint plugin class: `kernitus.plugin.OldCombatMechanics.OCMTestMain`.
- Tests run inside a real Paper server started by the Gradle `run-paper` plugin.
- RunServer output is redirected to `build/integration-test-logs/<version>.log`; `checkTestResults<version>` prints only a compact summary/failures to the console.
- `KotestRunner` writes `plugins/OldCombatMechanicsTest/test-failures.txt` so CI can surface failure reasons without opening the full server log.
- PacketEvents is shaded into the plugin; integration tests do not inject external packet libraries.
- `relocateIntegrationTestClasses` relocates PacketEvents references in test classes only.
- `integrationTestJar` embeds relocated test classes plus runtime dependencies, excluding PacketEvents so the test plugin resolves PacketEvents from the main plugin’s shaded copy.
- `PacketCancellationIntegrationTest` uses a cancellable `CompletableFuture.await()` and drives PacketEvents via `PacketEventsImplHelper.handleClientBoundPacket` with a synthetic `User`.
- `PacketCancellationIntegrationTest` sets the Bukkit player on the synthetic PacketEvents event so module listeners can match `PacketSendEvent#getPlayer`.
- Matrix task: `integrationTest` depends on `integrationTestMatrix`.
- `OCMTestMain` writes `plugins/OldCombatMechanicsTest/test-results.txt` containing `PASS` or `FAIL`.

## Version matrix and Java selection

- Config is in `build.gradle.kts`.
- `integrationTestVersions` comes from the property or defaults.
- `requiredJavaVersion` selects Java based on version.
- Pre-1.13 versions use `integrationTestJavaVersionLegacyPre13` (default 8).
- Modern versions use Java 25 if `>=1.20.5`, else Java 17.
- Legacy vanilla jar cache: `downloadVanilla<version>` downloads Mojang server jars for `<=1.12` and writes `run/<version>/cache/mojang_<version>.jar`.
- Paper 1.12 sometimes fails to download the legacy vanilla jar from old Mojang endpoints; the custom `downloadVanilla` task uses the v2 manifest.

## Kotlin runner split

- Kotest 6 (Java 11+) is used for 1.19.2 and 1.21.11.
- Kotest runner class: `kernitus.plugin.OldCombatMechanics.KotestRunner`.
- Project config: `KotestProjectConfig`.
- Java 8 uses `kernitus.plugin.OldCombatMechanics.LegacyTestRunner`.
- The Java 8 path is currently a smoke test only, verifying the plugin is enabled and `WeaponDamages` is loaded.

## Fake player notes

- Primary implementation: `src/integrationTest/kotlin/kernitus/plugin/OldCombatMechanics/FakePlayer.kt`.
- It uses `xyz.jpenilla:reflection-remapper` to map modern NMS names.
- On legacy servers, mappings are unavailable and code falls back to `ReflectionRemapper.noop()`.
- As-is, `FakePlayer` uses modern NMS class names; Paper 1.12 needs a dedicated fake-player path or version-aware class mapping.
- Fake player implementations use simulated login/network plumbing with `EmbeddedChannel` and manual login/join/quit events, not a real networked client.
- `FakePlayer` uses a plain `EmbeddedChannel` with dummy `decoder`/`encoder` handlers so PacketEvents treats it as fake and does not disallow login.
- Modern fake players schedule a manual NMS tick, preferring `doTick`, then `tick`, then `baseTick`.
- The tick shim invokes `baseTick` while burning to ensure fire tick damage events still occur on Paper 1.21+, and prefers normal ticking when in water so extinguish logic can run.
- `FakePlayer` prefers `PlayerList.placeNewPlayer` and forces a world add when the Bukkit world does not report the fake player entity after placement.
- `FakePlayer` clears invulnerability/instabuild abilities after spawn, plus a legacy fallback.

## Test harness shortcuts

- Synthetic event tests: `GoldenAppleIntegrationTest`, `OldPotionEffectsIntegrationTest`, `OldArmourDurabilityIntegrationTest`, `PlayerKnockbackIntegrationTest`, `SwordBlockingIntegrationTest`, and `SwordSweepIntegrationTest`.
- Direct module handler tests: `PlayerKnockbackIntegrationTest` and `SwordSweepIntegrationTest`.
- `AttributeModifierCompat` synthesises a fallback attack-damage modifier from `NewWeaponDamage` when API attributes are missing.

## Registration and notable test history

- `KotestRunner` must include each new integration test class explicitly.
- `OldToolDamageMobIntegrationTest`, `WeaponDurabilityIntegrationTest`, `DisableOffhandIntegrationTest`, `ToolDamageTooltipIntegrationTest`, `PacketCancellationIntegrationTest`, `ModesetRulesIntegrationTest`, `ConfigMigrationIntegrationTest`, `ChorusFruitIntegrationTest`, `DisableOffhandReflectionIntegrationTest`, `AttackRangeIntegrationTest`, `PaperSwordBlockingDamageReductionIntegrationTest`, and `ConsumableComponentIntegrationTest` have all been registered historically.
- `Kotest` filters (`kotest.filter.specs`, `kotest.filter.tests`) pass through Gradle into the run-paper JVM args.
- `WeaponDurabilityIntegrationTest` and `OldToolDamageMobIntegrationTest` resolve debug paths relative to the repo root rather than hard-coded home paths.
- `1.9` integration tests are currently on hold per user request.
- 1.21.11 servers may log hostname and Unsafe warnings; tests can still pass.

## PacketEvents and packet cancellation history

- `ModuleSwordSweepParticles` and `ModuleAttackSounds` use PacketEvents listeners/wrappers instead of ProtocolLib.
- `PacketCancellationIntegrationTest` builds a `PacketSendEvent` directly from the transformed buffer, sets packet id/type explicitly, and dispatches it through the PacketEvents event manager.
- PacketEvents dependency moved to `2.11.2-SNAPSHOT` from CodeMC snapshots for 1.21.11 support.

## Sword blocking and consumable component history

- Paper-only sword blocking uses runtime-gated helper `kernitus.plugin.OldCombatMechanics.paper.PaperSwordBlocking` to apply consumable and blocking components via cached reflection.
- 1.8-style reduction is applied via `EntityDamageByEntityListener` using the `BLOCKING` modifier, while legacy/non-Paper keeps the shield swap path.
- `ModuleSwordBlocking` gates the Paper consumable animation by PacketEvents client version `>=1.20.5`; older clients fall back to offhand shield behaviour.
- Unknown PacketEvents client versions fall back to shield behaviour only when a PacketEvents `User` exists; early-login/synthetic players keep animation support unless resolver failure requires fail-safe fallback.
- Inventory-click and drag handlers follow a minimal-mutation policy; lifecycle and transition handlers clean stale consumable state.
- Lifecycle cleanup covers modeset change, reload, world change, quit, join, held-slot changes, and swap paths.
- `sword-blocking.paper-animation` can hard-disable the Paper animation path and force legacy shield fallback.
- Legacy shield-drop reconciliation is safety-first and avoids guessing plain shield drops as temporary shields when marker APIs are unavailable.

## Fire aspect and fire tick notes

- `FireAspectOverdamageIntegrationTest` uses a Zombie victim for real fire tick sampling with boosted max health.
- Initial tests fire synthetic `EntityDamageEvent` with `FIRE_TICK` for deterministic baseline checks.
- Paper 1.12 applies attack-cooldown scaling before Bukkit damage events; tests wait a few ticks before the first attack to stabilise the first hit.
- Extra coverage includes fire resistance, fire-immune victims, water extinguish behaviour, fire protection versus protection, and alternating attackers during invulnerability.
- Legacy player-specific fire tick sampling differs, so player afterburn-vs-environment comparisons are no-ops on legacy; Zombie variants still run across versions.

## Miscellaneous historical fixes

- Removed dead reflection utility `ClassType` and unused `Reflector#getClass(ClassType, String)` overload.
- `SpigotFunctionChooser` now only falls back for compatibility-style failures and rethrows ordinary runtime logic failures.
- `AttackCompat` requires observable living-target hit signals for Bukkit `Player#attack` success and treats boolean-return NMS methods returning `false` as failed attempts.
- Several modules replaced per-player/per-hit scheduled tasks with shared or lazy-pruned maps: sword blocking dedupe, fishing rod velocity, attack cooldown tracking, player knockback, shield damage reduction, old armour durability, player regen, disable enderpearl cooldown, and stored last-damage expiry.
- `ModuleLoader` clears the static module list on initialise to prevent duplicate registrations after hot reloads.
- `README` includes licence notes: source remains MPL-2.0, while pre-built jars bundling PacketEvents are distributed under GPLv3; builds without PacketEvents can remain MPL-2.0.
