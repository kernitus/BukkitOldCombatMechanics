/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.util.Vector
import kotlin.math.sqrt


/**
 * Brings back the old fishing-rod knockback.
 */
class ModuleFishingKnockback(plugin: OCMMain) : OCMModule(plugin, "old-fishing-knockback") {
    private val getHookFunction: SpigotFunctionChooser<PlayerFishEvent, Any?, Entity?>
    private val getHitEntityFunction: SpigotFunctionChooser<ProjectileHitEvent, Any, Entity?>
    private var knockbackNonPlayerEntities = false

    init {
        reload()

        getHookFunction = SpigotFunctionChooser.apiCompatReflectionCall(
            { e, _ -> e.hook }, PlayerFishEvent::class.java, "getHook"
        )
        getHitEntityFunction = SpigotFunctionChooser.apiCompatCall({ e, _ -> e.hitEntity }, { e, _ ->
            val hookEntity: Entity = e.entity
            val world = hookEntity.world
            world.getNearbyEntities(hookEntity.location, 0.25, 0.25, 0.25).stream()
                .filter { entity: Entity? -> !knockbackNonPlayerEntities && entity is Player }.findFirst().orElse(null)
        })
    }

    override fun reload() {
        knockbackNonPlayerEntities = isSettingEnabled("knockbackNonPlayerEntities")
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onRodLand(event: ProjectileHitEvent) {
        val hookEntity: Entity = event.entity
        val world = hookEntity.world

        // FISHING_HOOK -> FISHING_BOBBER in >=1.20.5
        val fishingBobberType = try {
            EntityType.FISHING_BOBBER
        } catch (e: NoSuchFieldError) {
            EntityType.valueOf("FISHING_HOOK")
        }
        if (event.entityType != fishingBobberType) return

        val hook = hookEntity as FishHook

        if (hook.shooter !is Player) return
        val rodder = hook.shooter as Player
        if (!isEnabled(rodder)) return

        val hitEntity = getHitEntityFunction.apply(event) ?: return

        // If no entity was hit

        if (hitEntity !is LivingEntity) return
        if (!knockbackNonPlayerEntities && hitEntity !is Player) return

        // Do not move Citizens NPCs
        // See https://wiki.citizensnpcs.co/API#Checking_if_an_entity_is_a_Citizens_NPC
        if (hitEntity.hasMetadata("NPC")) return


        if (!knockbackNonPlayerEntities) {
            val player = hitEntity as Player

            debug("You were hit by a fishing rod!", player)

            if (player == rodder) return

            if (player.gameMode == GameMode.CREATIVE) return
        }

        // Check if cooldown time has elapsed
        if (hitEntity.noDamageTicks > hitEntity.maximumNoDamageTicks / 2f) return

        var damage = module().getDouble("damage")
        if (damage < 0) damage = 0.0001

        hitEntity.damage(damage, rodder)
        hitEntity.setVelocity(
            calculateKnockbackVelocity(
                hitEntity.getVelocity(), hitEntity.getLocation(), hook.location
            )
        )
    }

    private fun calculateKnockbackVelocity(currentVelocity: Vector, player: Location, hook: Location): Vector {
        var xDistance = hook.x - player.x
        var zDistance = hook.z - player.z

        // ensure distance is not zero and randomise in that case (I guess?)
        while (xDistance * xDistance + zDistance * zDistance < 0.0001) {
            xDistance = (Math.random() - Math.random()) * 0.01
            zDistance = (Math.random() - Math.random()) * 0.01
        }

        val distance = sqrt(xDistance * xDistance + zDistance * zDistance)

        var y = currentVelocity.y / 2
        var x = currentVelocity.x / 2
        var z = currentVelocity.z / 2

        // Normalise distance to have similar knockback, no matter the distance
        x -= xDistance / distance * 0.4

        // slow the fall or throw upwards
        y += 0.4

        // Normalise distance to have similar knockback, no matter the distance
        z -= zDistance / distance * 0.4

        // do not shoot too high up
        if (y >= 0.4) y = 0.4

        return Vector(x, y, z)
    }

    /**
     * This is to cancel dragging the entity closer when you reel in
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    private fun onReelIn(e: PlayerFishEvent) {
        if (e.state != PlayerFishEvent.State.CAUGHT_ENTITY) return

        val cancelDraggingIn = module().getString("cancelDraggingIn", "players")!!
        val isPlayer = e.caught is HumanEntity
        if ((cancelDraggingIn == "players" && isPlayer) || cancelDraggingIn == "mobs" && !isPlayer || cancelDraggingIn == "all") {
            getHookFunction.apply(e)!!.remove() // Remove the bobber and don't do anything else
            e.isCancelled = true
        }
    }
}