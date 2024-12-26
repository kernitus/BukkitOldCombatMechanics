/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector.versionIsNewerOrEqualTo
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.HumanEntity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerVelocityEvent
import org.bukkit.util.Vector
import java.util.*
import java.util.function.Consumer
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Reverts knockback formula to 1.8.
 * Also disables netherite knockback resistance.
 */
class ModulePlayerKnockback(plugin: OCMMain) : OCMModule(plugin, "old-player-knockback") {
    private var knockbackHorizontal = 0.0
    private var knockbackVertical = 0.0
    private var knockbackVerticalLimit = 0.0
    private var knockbackExtraHorizontal = 0.0
    private var knockbackExtraVertical = 0.0
    private var netheriteKnockbackResistance = false

    private val playerKnockbackHashMap: MutableMap<UUID, Vector> = WeakHashMap()

    init {
        reload()
    }

    override fun reload() {
        knockbackHorizontal = module()!!.getDouble("knockback-horizontal", 0.4)
        knockbackVertical = module()!!.getDouble("knockback-vertical", 0.4)
        knockbackVerticalLimit = module()!!.getDouble("knockback-vertical-limit", 0.4)
        knockbackExtraHorizontal = module()!!.getDouble("knockback-extra-horizontal", 0.5)
        knockbackExtraVertical = module()!!.getDouble("knockback-extra-vertical", 0.1)
        netheriteKnockbackResistance =
            module()!!.getBoolean("enable-knockback-resistance", false) && versionIsNewerOrEqualTo(1, 16, 0)
    }

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        playerKnockbackHashMap.remove(e.player.uniqueId)
    }

    // Vanilla does its own knockback, so we need to set it again.
    // priority = lowest because we are ignoring the existing velocity, which could break other plugins
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayerVelocityEvent(event: PlayerVelocityEvent) {
        val uuid = event.player.uniqueId
        if (!playerKnockbackHashMap.containsKey(uuid)) return
        event.velocity = playerKnockbackHashMap[uuid]!!
        playerKnockbackHashMap.remove(uuid)
    }

    @EventHandler
    fun onEntityDamage(event: EntityDamageEvent) {
        // Disable netherite kb, the knockback resistance attribute makes the velocity event not be called
        val entity = event.entity
        if (entity !is Player || netheriteKnockbackResistance) return
        val damagee = entity

        // This depends on the attacker's combat mode
        if (event.cause == DamageCause.ENTITY_ATTACK
            && event is EntityDamageByEntityEvent
        ) {
            val damager = event.damager
            if (!isEnabled(damager)) return
        } else {
            if (!isEnabled(damagee)) return
        }

        val attribute = damagee.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE)
        attribute!!.modifiers.forEach(Consumer { modifier: AttributeModifier? ->
            attribute.removeModifier(
                modifier!!
            )
        })
    }

    // Monitor priority because we don't modify anything here, but apply on velocity change event
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onEntityDamageEntity(event: EntityDamageByEntityEvent) {
        val damager = event.damager as? LivingEntity ?: return
        val attacker = damager

        val damagee = event.entity as? Player ?: return
        val victim = damagee

        if (event.cause != DamageCause.ENTITY_ATTACK) return
        if (event.getDamage(DamageModifier.BLOCKING) > 0) return

        if (attacker is HumanEntity) {
            if (!isEnabled(attacker)) return
        } else if (!isEnabled(victim)) return

        // Figure out base knockback direction
        val attackerLocation = attacker.location
        val victimLocation = victim.location
        var d0 = attackerLocation.x - victimLocation.x
        var d1: Double

        d1 = attackerLocation.z - victimLocation.z
        while (d0 * d0 + d1 * d1 < 1.0E-4) {
            d0 = (Math.random() - Math.random()) * 0.01
            d1 = (Math.random() - Math.random()) * 0.01
        }

        val magnitude = sqrt(d0 * d0 + d1 * d1)

        // Get player knockback before any friction is applied
        val playerVelocity = victim.velocity

        // Apply friction, then add base knockback
        playerVelocity.setX((playerVelocity.x / 2) - (d0 / magnitude * knockbackHorizontal))
        playerVelocity.setY((playerVelocity.y / 2) + knockbackVertical)
        playerVelocity.setZ((playerVelocity.z / 2) - (d1 / magnitude * knockbackHorizontal))

        // Calculate bonus knockback for sprinting or knockback enchantment levels
        val equipment = attacker.equipment
        if (equipment != null) {
            val heldItem =
                if (equipment.itemInMainHand.type == Material.AIR) equipment.itemInOffHand else equipment.itemInMainHand

            var bonusKnockback = heldItem.getEnchantmentLevel(Enchantment.KNOCKBACK)
            if (attacker is Player && attacker.isSprinting) ++bonusKnockback

            if (playerVelocity.y > knockbackVerticalLimit) playerVelocity.setY(knockbackVerticalLimit)

            if (bonusKnockback > 0) { // Apply bonus knockback
                playerVelocity.add(
                    Vector(
                        (-sin((attacker.location.yaw * 3.1415927f / 180.0f).toDouble()) * bonusKnockback.toFloat() * knockbackExtraHorizontal),
                        knockbackExtraVertical,
                        cos((attacker.location.yaw * 3.1415927f / 180.0f).toDouble()) * bonusKnockback.toFloat() * knockbackExtraHorizontal
                    )
                )
            }
        }

        if (netheriteKnockbackResistance) {
            // Allow netherite to affect the horizontal knockback. Each piece of armour yields 10% resistance
            val resistance = 1 - victim.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE)!!
                .value
            playerVelocity.multiply(Vector(resistance, 1.0, resistance))
        }

        val victimId = victim.uniqueId

        // Knockback is sent immediately in 1.8+, there is no reason to send packets manually
        playerKnockbackHashMap[victimId] = playerVelocity

        // Sometimes PlayerVelocityEvent doesn't fire, remove data to not affect later events if that happens
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { playerKnockbackHashMap.remove(victimId) }, 1)
    }
}