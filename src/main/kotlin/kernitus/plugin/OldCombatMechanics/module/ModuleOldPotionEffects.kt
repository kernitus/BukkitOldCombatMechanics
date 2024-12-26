/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils.loadPotionDurationsList
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionDurations
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionTypeCompat
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionTypeCompat.Companion.fromPotionMeta
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockDispenseEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.potion.PotionData
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.potion.PotionType

/**
 * Allows configurable potion effect durations.
 */
class ModuleOldPotionEffects(plugin: OCMMain) : OCMModule(plugin, "old-potion-effects") {
    private var durations: Map<PotionTypeCompat, PotionDurations>? = null

    companion object {
        private val EXCLUDED_POTION_TYPES: Set<PotionTypeCompat> = mutableSetOf( // Base potions without any effect
            PotionTypeCompat("AWKWARD"),
            PotionTypeCompat("MUNDANE"),
            PotionTypeCompat("THICK"),
            PotionTypeCompat("WATER"),  // Instant potions with no further effects
            PotionTypeCompat("HARMING"),
            PotionTypeCompat("STRONG_HARMING"),
            PotionTypeCompat("HEALING"),
            PotionTypeCompat("STRONG_HEALING"),  // This type doesn't exist anymore >1.20.5, is specially handled in compat class
            PotionTypeCompat("UNCRAFTABLE")
        )
    }

    init {
        reload()
    }

    override fun reload() {
        durations = loadPotionDurationsList(module())
    }

    /**
     * Change the duration using values defined in config for drinking potions
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun onPlayerDrinksPotion(event: PlayerItemConsumeEvent) {
        val player = event.player
        if (!isEnabled(player)) return

        val potionItem = event.item
        if (potionItem.type != Material.POTION) return

        adjustPotion(potionItem, false)
        event.setItem(potionItem)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPotionDispense(event: BlockDispenseEvent) {
        if (!isEnabled(event.block.world)) return

        val item = event.item
        val material = item.type

        if (material == Material.SPLASH_POTION || material == Material.LINGERING_POTION) adjustPotion(item, true)
    }

    // We change the potion on-the-fly just as it's thrown to be able to change the effect
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPotionThrow(event: PlayerInteractEvent) {
        val player = event.player
        if (!isEnabled(player)) return

        val action = event.action
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return

        val item = event.item ?: return

        val material = item.type
        if (material == Material.SPLASH_POTION || material == Material.LINGERING_POTION) adjustPotion(item, true)
    }

    /**
     * Sets custom potion duration and effects
     *
     * @param potionItem The potion item with adjusted duration and effects
     */
    @Suppress("DEPRECATION")
    private fun adjustPotion(potionItem: ItemStack, splash: Boolean) {
        val potionMeta = potionItem.itemMeta as PotionMeta? ?: return

        val potionTypeCompat = fromPotionMeta(potionMeta)

        if (EXCLUDED_POTION_TYPES.contains(potionTypeCompat)) return

        val duration = getPotionDuration(potionTypeCompat, splash)
        if (duration == null) {
            debug("Potion type " + potionTypeCompat.newName + " not found in config, leaving as is...")
            return
        }

        var amplifier = if (potionTypeCompat.isStrong) 1 else 0

        if (potionTypeCompat == PotionTypeCompat("WEAKNESS")) {
            // Set level to 0 so that it doesn't prevent the EntityDamageByEntityEvent from being called
            // due to damage being lower than 0 as some 1.9 weapons deal less damage
            amplifier = -1
        }

        val potionType = potionTypeCompat.type
        val potionEffects: List<PotionEffectType> = potionType?.getPotionEffectTypes() ?: emptyList()

        for (effectType in potionEffects) {
            potionMeta.addCustomEffect(PotionEffect(effectType, duration, amplifier), false)
        }

        try { // For >=1.20
            potionMeta.basePotionType = PotionType.WATER
        } catch (e: NoSuchMethodError) {
            potionMeta.basePotionData = PotionData(PotionType.WATER)
        }

        potionItem.setItemMeta(potionMeta)
    }

    private fun PotionType.getPotionEffectTypes(): List<PotionEffectType> {
        return try {
            this.potionEffects.map { it.type }
        } catch (e: NoSuchMethodError) {
            listOf(this.effectType!!)
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamageByEntity(event: OCMEntityDamageByEntityEvent) {
        val damager = event.damager
        if (!isEnabled(damager, event.damagee)) return

        if (event.hasWeakness) {
            event.isWeaknessModifierMultiplier = module().getBoolean("weakness.multiplier")
            val newWeaknessModifier = module().getDouble("weakness.modifier")
            event.weaknessModifier = newWeaknessModifier
            event.weaknessLevel = 1
            debug(
                "Old weakness modifier: " + event.weaknessLevel +
                        " New: " + newWeaknessModifier, damager
            )
        }

        val strengthModifier = event.strengthModifier

        if (strengthModifier > 0) {
            event.isStrengthModifierMultiplier = module().getBoolean("strength.multiplier")
            event.isStrengthModifierAddend = module().getBoolean("strength.addend")
            val newStrengthModifier = module().getDouble("strength.modifier")
            event.strengthModifier = newStrengthModifier
            debug("Old strength modifier: $strengthModifier New: $newStrengthModifier", damager)
        }
    }

    private fun getPotionDuration(potionTypeCompat: PotionTypeCompat, splash: Boolean): Int? {
        val potionDurations = durations!![potionTypeCompat] ?: return null
        val duration = if (splash) potionDurations.splash else potionDurations.drinkable

        debug("Potion type: " + potionTypeCompat.newName + " Duration: " + duration + " ticks")

        return duration
    }

}
