/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier
import org.bukkit.event.player.PlayerItemDamageEvent
import org.bukkit.inventory.ItemStack
import java.util.*
import java.util.stream.Collectors

/**
 * Allows customising the shield damage reduction percentages.
 */
class ModuleShieldDamageReduction(plugin: OCMMain) : OCMModule(plugin, "shield-damage-reduction") {
    private var genericDamageReductionAmount = 0
    private var genericDamageReductionPercentage = 0
    private var projectileDamageReductionAmount = 0
    private var projectileDamageReductionPercentage = 0
    private val fullyBlocked: MutableMap<UUID, MutableList<ItemStack>> = WeakHashMap()

    init {
        reload()
    }

    override fun reload() {
        genericDamageReductionAmount = module()!!.getInt("generalDamageReductionAmount", 1)
        genericDamageReductionPercentage = module()!!.getInt("generalDamageReductionPercentage", 50)
        projectileDamageReductionAmount = module()!!.getInt("projectileDamageReductionAmount", 1)
        projectileDamageReductionPercentage = module()!!.getInt("projectileDamageReductionPercentage", 50)
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onItemDamage(e: PlayerItemDamageEvent) {
        val player = e.player
        if (!isEnabled(player)) return
        val uuid = player.uniqueId
        val item = e.item

        if (fullyBlocked.containsKey(uuid)) {
            val armour = fullyBlocked[uuid]!!
            // ItemStack.equals() checks material, durability and quantity to make sure nothing changed in the meantime
            // We're checking all the pieces this way just in case they're wearing two helmets or something strange
            val matchedPieces =
                armour.stream().filter { piece: ItemStack -> piece == item }.collect(Collectors.toList())
            armour.removeAll(matchedPieces)
            debug("Ignoring armour durability damage due to full block", player)
            if (!matchedPieces.isEmpty()) {
                e.isCancelled = true
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onHit(e: EntityDamageByEntityEvent) {
        val entity = e.entity as? Player ?: return

        val player = entity

        if (!isEnabled(e.damager, player)) return

        // Blocking is calculated after base and hard hat, and before armour etc.
        val baseDamage = e.getDamage(DamageModifier.BASE) + e.getDamage(DamageModifier.HARD_HAT)
        if (!shieldBlockedDamage(baseDamage, e.getDamage(DamageModifier.BLOCKING))) return

        val damageReduction = getDamageReduction(baseDamage, e.cause)
        e.setDamage(DamageModifier.BLOCKING, -damageReduction)
        val currentDamage = baseDamage - damageReduction

        debug("Blocking: $baseDamage - $damageReduction = $currentDamage", player)
        debug("Blocking: $baseDamage - $damageReduction = $currentDamage")

        val uuid = player.uniqueId

        if (currentDamage <= 0) { // Make sure armour is not damaged if fully blocked
            val armour =
                Arrays.stream(player.inventory.armorContents).filter { obj: ItemStack? -> Objects.nonNull(obj) }
                    .collect(
                        Collectors.toList()
                    )
            fullyBlocked[uuid] = armour

            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                fullyBlocked.remove(uuid)
                debug("Removed from fully blocked set!", player)
            }, 1L)
        }
    }

    private fun getDamageReduction(damage: Double, damageCause: DamageCause): Double {
        // 1.8 NMS code, where f is damage done, to calculate new damage.
        // f = (1.0F + f) * 0.5F;

        // We subtract, to calculate damage reduction instead of new damage

        var reduction =
            damage - (if (damageCause == DamageCause.PROJECTILE) projectileDamageReductionAmount else genericDamageReductionAmount)

        // Reduce to percentage
        reduction *= (if (damageCause == DamageCause.PROJECTILE) projectileDamageReductionPercentage else genericDamageReductionPercentage) / 100.0

        // Don't reduce by more than the actual damage done
        // As far as I can tell this is not checked in 1.8NMS, and if the damage was low enough
        // blocking would lead to higher damage. However, this is hardly the desired result.
        if (reduction < 0) reduction = 0.0

        return reduction
    }

    private fun shieldBlockedDamage(attackDamage: Double, blockingReduction: Double): Boolean {
        // Only reduce damage if they were hit head on, i.e. the shield blocked some of the damage
        // This also takes into account damages that are not blocked by shields
        return attackDamage > 0 && blockingReduction < 0
    }
}
