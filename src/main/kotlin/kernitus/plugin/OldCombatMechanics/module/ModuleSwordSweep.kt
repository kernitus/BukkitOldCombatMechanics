/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.damage.NewWeaponDamage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import org.bukkit.inventory.ItemStack
import org.bukkit.scheduler.BukkitTask

/**
 * A module to disable the sweep attack.
 */
class ModuleSwordSweep(plugin: OCMMain) : OCMModule(plugin, "disable-sword-sweep") {
    private val sweepLocations: MutableList<Location> = ArrayList()
    private var sweepDamageCause: DamageCause? = null
    private var task: BukkitTask? = null

    init {
        sweepDamageCause = try {
            // Available from 1.11 onwards
            DamageCause.valueOf("ENTITY_SWEEP_ATTACK")
        } catch (e: IllegalArgumentException) {
            null
        }

        reload()
    }

    override fun reload() {
        // we didn't set anything up in the first place
        if (sweepDamageCause != null) return

        if (task != null) task!!.cancel()

        task = Bukkit.getScheduler().runTaskTimer(plugin, Runnable { sweepLocations.clear() }, 0, 1)
    }


    //Changed from HIGHEST to LOWEST to support DamageIndicator plugin
    @EventHandler(priority = EventPriority.LOWEST)
    fun onEntityDamaged(e: EntityDamageByEntityEvent) {
        val damager = e.damager as? Player ?: return

        if (!isEnabled(damager, e.entity)) return

        if (sweepDamageCause != null) {
            if (e.cause == sweepDamageCause) {
                e.isCancelled = true
                debug("Sweep cancelled", damager)
            }
            // sweep attack detected or not, we do not need to fall back to the guessing implementation
            return
        }

        val attacker = e.damager as Player
        val weapon = attacker.inventory.itemInMainHand

        if (isHoldingSword(weapon.type)) onSwordAttack(e, attacker, weapon)
    }

    private fun onSwordAttack(e: EntityDamageByEntityEvent, attacker: Player, weapon: ItemStack) {
        //Disable sword sweep
        val attackerLocation = attacker.location

        var level = 0

        try { //In a try catch for servers that haven't updated
            level = weapon.getEnchantmentLevel(Enchantment.SWEEPING_EDGE)
        } catch (ignored: NoSuchFieldError) {
        }

        val damage = NewWeaponDamage.getDamage(weapon.type) * level / (level + 1) + 1

        if (e.damage == damage.toDouble()) {
            // Possibly a sword-sweep attack
            if (sweepLocations.contains(attackerLocation)) {
                debug("Cancelling sweep...", attacker)
                e.isCancelled = true
            }
        } else {
            sweepLocations.add(attackerLocation)
        }
    }

    private fun isHoldingSword(mat: Material): Boolean {
        return mat.toString().endsWith("_SWORD")
    }
}
