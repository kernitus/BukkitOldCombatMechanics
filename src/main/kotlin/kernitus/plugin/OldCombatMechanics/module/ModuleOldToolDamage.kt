/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.Messenger
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils.getNewSharpnessDamage
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils.getOldSharpnessDamage
import kernitus.plugin.OldCombatMechanics.utilities.damage.NewWeaponDamage
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.entity.EntityDamageEvent.DamageCause
import kotlin.math.abs

/**
 * Restores old tool damage.
 */
class ModuleOldToolDamage(plugin: OCMMain) : OCMModule(plugin, "old-tool-damage") {
    companion object {
        private val WEAPONS = arrayOf("sword", "axe", "pickaxe", "spade", "shovel", "hoe")
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityDamaged(event: OCMEntityDamageByEntityEvent) {
        val damager = event.damager
        if (event.cause == DamageCause.THORNS) return

        if (!isEnabled(damager, event.damagee)) return

        val weapon = event.weapon
        val weaponMaterial = weapon!!.type
        debug("Weapon material: $weaponMaterial")

        if (!isWeapon(weaponMaterial)) return

        // If damage was not what we expected, ignore it because it's probably a custom weapon or from another plugin
        val oldBaseDamage = event.baseDamage
        val expectedBaseDamage = NewWeaponDamage.getDamage(weaponMaterial).toDouble()
        // We check difference as calculation inaccuracies can make it not match
        if (abs(oldBaseDamage - expectedBaseDamage) > 0.0001) {
            debug("Expected $expectedBaseDamage got $oldBaseDamage ignoring weapon...")
            return
        }

        val newWeaponBaseDamage = WeaponDamages.getDamage(weaponMaterial)
        if (newWeaponBaseDamage <= 0) {
            debug("Unknown tool type: $weaponMaterial", damager)
            return
        }

        event.baseDamage = newWeaponBaseDamage
        Messenger.debug("Old tool damage: $oldBaseDamage New: $newWeaponBaseDamage")


        // Set sharpness to 1.8 damage value
        val sharpnessLevel = event.sharpnessLevel
        val newSharpnessDamage = if (module().getBoolean(
                "old-sharpness", true
            )
        ) getOldSharpnessDamage(sharpnessLevel) else getNewSharpnessDamage(sharpnessLevel)

        debug("Old sharpness damage: " + event.sharpnessDamage + " New: " + newSharpnessDamage, damager)
        event.sharpnessDamage = newSharpnessDamage

        // The mob enchantments damage remains the same and is linear, no need to recalculate it
    }

    private fun isWeapon(material: Material): Boolean {
        return WEAPONS.any { isOfType(material, it) }
    }

    private fun isOfType(mat: Material, type: String): Boolean {
        return mat.toString().endsWith("_" + type.uppercase())
    }

}
