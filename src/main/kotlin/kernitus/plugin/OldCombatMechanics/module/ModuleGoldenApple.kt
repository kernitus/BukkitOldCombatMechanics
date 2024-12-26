/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module

import com.google.common.collect.ImmutableSet
import kernitus.plugin.OldCombatMechanics.OCMMain
import kernitus.plugin.OldCombatMechanics.utilities.Messenger.send
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffectTypeCompat
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffectTypeCompat.Companion.fromNewName
import kernitus.plugin.OldCombatMechanics.versions.materials.MaterialRegistry.ENCHANTED_GOLDEN_APPLE
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.LivingEntity
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

/**
 * Customise the golden apple effects.
 */
class ModuleGoldenApple(plugin: OCMMain) : OCMModule(plugin, "old-golden-apples") {
    private lateinit var enchantedGoldenAppleEffects: List<PotionEffect>
    private lateinit var goldenAppleEffects: List<PotionEffect>
    private lateinit var enchantedAppleRecipe: ShapedRecipe

    private val lastEaten: MutableMap<UUID, LastEaten> = WeakHashMap()
    private lateinit var cooldown: Cooldown

    private lateinit var normalCooldownMessage: String
    private lateinit var enchantedCooldownMessage: String

    companion object {
        // Default apple effects
        // Gapple: absorption I, regen II
        private val gappleEffects: Set<PotionEffectType> = ImmutableSet.of(
            PotionEffectType.ABSORPTION, PotionEffectType.REGENERATION
        )

        // Napple: absorption IV, regen II, fire resistance I, resistance I
        private val nappleEffects: Set<PotionEffectType?> = ImmutableSet.of(
            PotionEffectType.ABSORPTION,
            PotionEffectType.REGENERATION,
            PotionEffectType.FIRE_RESISTANCE,
            PotionEffectTypeCompat.RESISTANCE.potionEffectType
        )
        lateinit var instance: ModuleGoldenApple
    }

    init {
        instance = this
    }

    override fun reload() {
        normalCooldownMessage = module().getString("cooldown.message-normal") ?: ""
        enchantedCooldownMessage = module().getString("cooldown.message-enchanted") ?: ""

        cooldown = Cooldown(
            module().getLong("cooldown.normal"),
            module().getLong("cooldown.enchanted"),
            module().getBoolean("cooldown.is-shared")
        )
        lastEaten.clear()

        enchantedGoldenAppleEffects = getPotionEffects("enchanted-golden-apple-effects")
        goldenAppleEffects = getPotionEffects("golden-apple-effects")

        enchantedAppleRecipe = try {
            ShapedRecipe(
                NamespacedKey(plugin, "MINECRAFT"), ENCHANTED_GOLDEN_APPLE.newInstance()
            )
        } catch (e: NoClassDefFoundError) {
            ShapedRecipe(ENCHANTED_GOLDEN_APPLE.newInstance())
        }
        enchantedAppleRecipe!!.shape("ggg", "gag", "ggg").setIngredient('g', Material.GOLD_BLOCK)
            .setIngredient('a', Material.APPLE)

        registerCrafting()
    }

    private fun registerCrafting() {
        if (isEnabled() && module().getBoolean("enchanted-golden-apple-crafting")) {
            if (Bukkit.getRecipesFor(ENCHANTED_GOLDEN_APPLE.newInstance()).isNotEmpty()) return
            Bukkit.addRecipe(enchantedAppleRecipe)
            debug("Added napple recipe")
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPrepareItemCraft(e: PrepareItemCraftEvent) {
        val item = e.inventory.result ?: return

        if (ENCHANTED_GOLDEN_APPLE.isSame(item)) {
            val player = e.view.player

            if (isSettingEnabled("no-conflict-mode")) return

            if (!isEnabled(player) || !isSettingEnabled("enchanted-golden-apple-crafting")) e.inventory.result = null
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onItemConsume(e: PlayerItemConsumeEvent) {
        val player = e.player

        if (!isEnabled(player)) return

        val originalItem = e.item
        val consumedMaterial = originalItem.type

        if (consumedMaterial != Material.GOLDEN_APPLE && !ENCHANTED_GOLDEN_APPLE.isSame(originalItem)) return

        val uuid = player.uniqueId

        // Ensure the lastEatenInfo exists for the player
        val lastEatenInfo = lastEaten.computeIfAbsent(uuid) { LastEaten() }

        val remainingCooldown = cooldown.getRemainingCooldown(originalItem, lastEatenInfo)

        // If on cooldown send appropriate cooldown message
        if (remainingCooldown != null && remainingCooldown > 0) {
            val message: String = if (consumedMaterial == Material.GOLDEN_APPLE) {
                normalCooldownMessage
            } else {
                enchantedCooldownMessage
            }

            if (message.isNotEmpty()) {
                send(player, message.replace("%seconds%", remainingCooldown.toString()))
            }

            e.isCancelled = true
            return
        }

        // Update last eaten time
        lastEatenInfo.setForItem(originalItem)

        if (!isSettingEnabled("old-potion-effects")) return

        // Save player's current potion effects
        val previousPotionEffects = player.activePotionEffects

        val newEffects =
            if (ENCHANTED_GOLDEN_APPLE.isSame(originalItem)) enchantedGoldenAppleEffects else goldenAppleEffects
        val defaultEffects = if (ENCHANTED_GOLDEN_APPLE.isSame(originalItem)) nappleEffects else gappleEffects

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // Remove all potion effects the apple added
            player.activePotionEffects.map { it.type }.filter { defaultEffects.contains(it) }
                .forEach { player.removePotionEffect(it) }

            // Add previous potion effects from before eating the apple
            player.addPotionEffects(previousPotionEffects)
            // Add new custom effects from eating the apple
            applyEffects(player, newEffects)
        }, 1L)
    }


    private fun applyEffects(target: LivingEntity, newEffects: List<PotionEffect>) {
        for (newEffect in newEffects) {
            // Find the existing effect of the same type with the highest amplifier
            val highestExistingEffect =
                target.activePotionEffects.filter { it.type === newEffect.type }.maxByOrNull { it.amplifier }

            if (highestExistingEffect != null) {
                // If the new effect has a higher amplifier, apply it
                if (newEffect.amplifier > highestExistingEffect.amplifier) {
                    target.addPotionEffect(newEffect)
                } else if (newEffect.amplifier == highestExistingEffect.amplifier && newEffect.duration > highestExistingEffect.duration) {
                    target.addPotionEffect(newEffect)
                }
                // If the new effect has a lower amplifier or shorter/equal duration, do nothing
            } else {
                // If there is no existing effect of the same type, apply the new effect
                target.addPotionEffect(newEffect)
            }
        }
    }


    private fun getPotionEffects(path: String): List<PotionEffect> {
        val appleEffects: MutableList<PotionEffect> = mutableListOf()

        val sect = module().getConfigurationSection(path)
        if (sect != null) {
            for (key in sect.getKeys(false)) {
                val duration = sect.getInt("$key.duration") * 20 // Convert seconds to ticks
                val amplifier = sect.getInt("$key.amplifier")

                val type = fromNewName(key)
                Objects.requireNonNull(type, String.format("Invalid potion effect type '%s'!", key))

                val fx = PotionEffect(type!!, duration, amplifier)
                appleEffects.add(fx)
            }
        }
        return appleEffects
    }

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        lastEaten.remove(e.player.uniqueId)
    }

    /**
     * Get player's current golden apple cooldown
     *
     * @param playerUUID The UUID of the player to check the cooldown for.
     * @return The remaining cooldown time in seconds, or 0 if there is no cooldown, or it has expired.
     */
    fun getGappleCooldown(playerUUID: UUID): Long {
        val lastEatenInfo: LastEaten = lastEaten[playerUUID] ?: return 0
        return cooldown.getRemainingCooldown(ItemStack(Material.GOLDEN_APPLE), lastEatenInfo) ?: 0
    }

    /**
     * Get player's current enchanted golden apple cooldown
     *
     * @param playerUUID The UUID of the player to check the cooldown for.
     * @return The remaining cooldown time in seconds, or 0 if there is no cooldown, or it has expired.
     */
    fun getNappleCooldown(playerUUID: UUID): Long {
        val lastEatenInfo = lastEaten[playerUUID] ?: return 0
        return cooldown.getRemainingCooldown(ENCHANTED_GOLDEN_APPLE.newInstance(), lastEatenInfo) ?: 0
    }

    private class LastEaten {
        var lastNormalEaten: Instant? = null
        var lastEnchantedEaten: Instant? = null

        fun getForItem(item: ItemStack): Instant? =
            if (ENCHANTED_GOLDEN_APPLE.isSame(item)) lastEnchantedEaten else lastNormalEaten

        val newestEatTime: Instant?
            get() = listOfNotNull(lastNormalEaten, lastEnchantedEaten).maxOrNull()

        fun setForItem(item: ItemStack) {
            val now = Instant.now()
            if (ENCHANTED_GOLDEN_APPLE.isSame(item)) {
                lastEnchantedEaten = now
            } else {
                lastNormalEaten = now
            }
        }
    }

    private data class Cooldown(val normal: Long, val enchanted: Long, val sharedCooldown: Boolean) {
        fun getCooldownForItem(item: ItemStack): Long {
            return if (ENCHANTED_GOLDEN_APPLE.isSame(item)) enchanted else normal
        }

        /**
         * Returns the remaining cooldown time in seconds if the player is on cooldown, or null if not on cooldown.
         */
        fun getRemainingCooldown(item: ItemStack, lastEaten: LastEaten): Long? {
            val lastEatTime: Instant? = if (sharedCooldown) lastEaten.newestEatTime else lastEaten.getForItem(item)
            val cooldownDuration = getCooldownForItem(item)
            return lastEatTime?.let {
                val secondsSinceEat = ChronoUnit.SECONDS.between(it, Instant.now())
                val remaining = cooldownDuration - secondsSinceEat
                if (remaining > 0) remaining else null
            }
        }
    }
}
