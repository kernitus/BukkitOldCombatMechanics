# Remaining public API audit

Evaluated on 26 October 2026 for slice 7 of the Paper API first compatibility work. Retain the existing adapters in all areas below. No production or test code changes are justified by the inspected public contracts. This closes the bounded API audit; it does not certify every retained internal implementation against future server changes.

## Evidence boundary

Inspected the pinned `paper-api:26.3.build.223-stable` class signatures and bytecode, the local Paper 26.3 server bytecode, the repository implementations, and the official references linked below. The online 26.3 Javadocs currently report differing build numbers; moving `main` source links are supporting references. The local artefacts identify the exact inspected implementation:

| Artefact | SHA-256 |
| --- | --- |
| `run/26.3/libraries/io/papermc/paper/paper-api/26.3.build.223-stable/paper-api-26.3.build.223-stable.jar` | `fa309e1f97827079ab40df93d4e5c6dd570137d13623b4655b0693eb0c27f226` |
| `run/26.3/versions/26.3/paper-26.3.jar` | `6552d037cb5ead1634498cf28d5ac2d63ffee9125867b47db5de8b2e48c4756d` |

Use `javap -p -classpath <api-jar> <class>` for the public surface and `javap -c -p -classpath <server-jar> <class>` for the implementation evidence below. These commands and `sha256sum` completed successfully. No server tests were run for this documentation-only result, as permitted by slice 7. Existing tests named below are future conversion gates, not newly claimed passes. Paper 1.9.4 remains the approved representative of the 1.9 family.

## Same-entity projectile insertion

`ProjectileInsertion` needs to reposition the original unborn projectile before section selection, without advancing insertion, changing cancellation ordering or replacing the entity. Chunk boundaries, the first collision, travelled distance and cleanup after late cancellation are part of that contract.

The [Entity API](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Entity.html) offers teleport and `spawnAt`; [RegionAccessor](https://jd.papermc.io/paper/26.3/org/bukkit/RegionAccessor.html) offers creation, spawning with a consumer, and adding an entity. Creation and spawn consumers operate on a newly created entity. `spawnAt` and `addEntity` perform insertion themselves. None is a position-only operation for a native launch already in progress. Local `CraftEntity.spawnAt` calls `setPos` and then `addFreshEntity` when invalid. Using it inside launch dispatch would advance insertion before remaining listeners can cancel it.

Local `CraftEntity.teleport0` returns false when native `valid` is false, before processing teleport flags. Local `Entity.setPosRaw` returns without moving while `updatingSectionStatus` is true. The [CraftEntity source](https://github.com/PaperMC/Paper/blob/main/paper-server/src/main/java/org/bukkit/craftbukkit/entity/CraftEntity.java) supports the teleport and spawn implementation evidence. [EntityAddToWorldEvent](https://jd.papermc.io/paper/26.3/com/destroystokyo/paper/event/entity/EntityAddToWorldEvent.html) supplies an entity and world, with no public movement-permission or section-selection boundary. Its presence does not establish that movement is safe inside its callback.

Disposition: retain detection of the native section implementation, the existing public teleport attempt before native `setPos`, legacy post-add handling and the next-tick relative-offset fallback. Do not reorder these attempts based solely on Paper branding. The fallback preserves travelled distance and velocity but approximates the first collision; it cannot repair a collision already processed. Existing pending-task cleanup and eligibility checks must remain. Any conversion requires native `ProjectileLaunchFidelityIntegrationTest` and `OldPotionThrowingIntegrationTest` evidence across current and legacy servers, including chunk boundaries and cancellation.

## Native inherited movement and launch snapshots

`NativeLaunchMovement` requires the vector actually inherited by `shootFromRotation`, its grounded vertical-component rule and the live per-world relative-projectile switch. `ProjectileLaunchCoordinator.snapshot` clones that result and captures geometry at the first attributed LOWEST callback. Later movement, ground-state, stance, teleport or configuration edits must not alter that launch.

Local `CraftEntity.getVelocity` reads `getDeltaMovement`. Local `ServerPlayer.getKnownMovement` instead reads `lastKnownClientMovement`, or a vehicle's known movement when the player is not its controlling passenger. Local `Projectile.shootFromRotation` uses `getKnownMovement`, `onGround` and `WorldConfiguration.misc.disableRelativeProjectileVelocity`. The inspected public `Entity`, `Player` and `World` surfaces provide no accessor for that complete inherited vector or the world switch. Public velocity and ground-state getters therefore cannot replace this operation as a whole. The [Entity API](https://jd.papermc.io/paper/26.3/org/bukkit/entity/Entity.html) describes velocity; it does not promise the client movement consumed by native launches.

Disposition: retain the read-only native adapter and the cloned launch snapshot. Do not mutate Paper configuration or reread movement at the final handler. Earlier foreign edits remain indistinguishable from the native state at the first observer. Failed native access continues to preserve native launch velocity.

Residual finding: inspected 26.3 `shootFromRotation` zeros any non-finite inherited vector through `Vec3.isFinite`; the retained adapter explicitly zeros NaN components only, following its documented 1.21.11 basis. Infinity equivalence has not been demonstrated. This audit does not silently change that numerical behaviour or claim complete 26.3 equivalence. A separate native invalid-movement regression is needed before a correction. Spread changes would additionally require the existing 8192-launch distribution case.

## Native held-stack identity and launch dispatch

`NativeLaunchIdentity` must recognise the actual native main-hand stack used by a native item invocation, reject an identical offhand stack and reject constructed or nested redispatched events. The coordinator separately checks an unborn projectile, shooter and original bow-event projectile identity.

[PlayerLaunchProjectileEvent](https://jd.papermc.io/paper/26.3/com/destroystokyo/paper/event/player/PlayerLaunchProjectileEvent.html) exposes the used item and projectile but no hand or native invocation token. The pinned `EntityShootBowEvent` exposes `getHand`, `getBow` and a replaceable projectile; a hand enum does not establish that the held native stack still matches the observed launch. Both events have constructors and can be dispatched by plugins. Public `ItemStack` equality, similarity and component comparisons describe values. The [CraftItemStack implementation](https://github.com/PaperMC/Paper/blob/main/paper-server/src/main/java/org/bukkit/craftbukkit/inventory/CraftItemStack.java) confirms equality can succeed for matching contents with different native handles.

Disposition: retain native handle identity and the item-dispatch stack boundary. A public hand getter alone cannot replace either guarantee. Keep original event identity, unborn checks and nested-dispatch rejection in the coordinator. Arbitrary reflective construction of a replacement unborn entity before the first observer remains indistinguishable through the event API. Native held-stack, offhand, foreign replacement and nested launch cases in `ProjectileLaunchFidelityIntegrationTest` remain conversion gates; stock Paper cannot prove custom native bow dispatch without a registered custom item.

## Damage and regeneration provenance

`CombatDamageProvenance` must claim the exact synchronous rod-damage event at the expected Bukkit dispatch depth. An earlier listener can cause native damage between the same entities before the outer event reaches OCM. Matching damager, victim and amount does not identify the outer operation. Weak exact-event chip records also require the incoming amount to remain OCM's tiny injection; equal-valued foreign damage or later foreign amount changes must retain ordinary blocking semantics.

The pinned `DamageSource` exposes type, causing and direct entities, location and `getDamageContext`. Its builder exposes `withDamageContext`, and `Damageable.damage(double, DamageSource)` can carry a source. This is a concrete candidate for marking a future OCM-created damage request, not evidence that native or foreign events already carry a trustworthy dispatch token. Replacing `damage(double, Entity)` would require proving equivalent native source construction, attribution, damage tags and nested source reuse on current and legacy runtimes. No such equivalence was established here. The [EntityDamageEvent source](https://github.com/PaperMC/Paper/blob/main/paper-api/src/main/java/org/bukkit/event/entity/EntityDamageEvent.java) confirms that source metadata is event data, with no native dispatch-depth accessor.

`ArrowKnockbackCompat` additionally checks the actual native arrow collision stack and dispatch depth after matching the current damage cause and shooter. Reusing a cause or source in a nested plugin knockback call must not qualify it as the arrow's base stage.

`ModulePlayerRegen.nativeFoodEvent` needs to distinguish native food processing from constructed SATIATED healing and REGEN exhaustion events, including nested calls. [EntityRegainHealthEvent](https://jd.papermc.io/paper/26.3/org/bukkit/event/entity/EntityRegainHealthEvent.html) exposes reason and `isFastRegen`, but its constructors also accept those values. The pinned `EntityExhaustionEvent` likewise accepts entity, reason and amount; it has no corresponding native food invocation identifier. These values cannot replace the FoodData or FoodMetaData stack boundary and single Bukkit dispatch check.

Disposition: retain exact-event ownership, scoped attempts, finally cleanup and native stack provenance. Continue using public regeneration rates and exhaustion mutation where already supported. Preserve later cancellation and amount edits, creative-mode native charging behaviour and the documented legacy exhaustion-cap ambiguity. A future source-context conversion requires native nested same-pair rod damage, foreign tiny damage, arrow collision and foreign knockback cases. Regeneration conversion requires `NativeRegenerationIntegrationTest` and `PlayerRegenIntegrationTest` native food ticks, nested foreign events and final cancellation checks.

## Downstream blocking recalculation

`BlockingDamageRecalculation` must replace BLOCKING and re-evaluate only subsequent defences with the server's captured functions, preserving foreign adjustments already applied to each modifier. The required calculation is the existing modifier plus the function's change between old and new incoming damage. It must compute all replacements before modifying the event, leaving it untouched if access or calculation fails.

The pinned `EntityDamageEvent` provides original and current modifier amounts, applicability, individual modifier setters and `setDamage(double)`. It has no public accessor for its captured functions or operation that recalculates starting after BLOCKING. Its bytecode and [official source](https://github.com/PaperMC/Paper/blob/main/paper-api/src/main/java/org/bukkit/event/entity/EntityDamageEvent.java) show `setDamage(double)` traversing the original modifier functions from BASE, including the original blocking function. Setting BLOCKING alone changes its value without recomputing later functions. Calling the base setter cannot express the required boundary.

Disposition: retain the cached `modifierFunctions` compatibility accessor and transactional calculation. Rebuilding armour, Resistance and enchantment functions from public attributes would lose the captured event context and foreign adjustments. Private-field changes remain a compatibility risk; failure returns false without partial mutation. Any replacement needs native blocking, armour, Resistance, enchantment, absorption, zero-damage and foreign-adjustment coverage from the existing defence and interaction suites.

## Guidance

Revisit these dispositions when a public API supplies the missing operation or identity guarantee, with independent capability detection and native equivalence tests. A new getter, hand label or event reason alone is insufficient. Retain the separate [client protocol evaluation](client-protocol-evaluation.md) for translation semantics. This audit authorises no entity replacement, fake-player rewrite or speculative event reordering.
