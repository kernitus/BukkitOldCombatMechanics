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
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.math.max

/**
 * Customise the golden apple effects.
 */
class ModuleGoldenApple(plugin: OCMMain) : OCMModule(plugin, "old-golden-apples") {
    private var enchantedGoldenAppleEffects: List<PotionEffect>? = null
    private var goldenAppleEffects: List<PotionEffect>? = null
    private var enchantedAppleRecipe: ShapedRecipe? = null

    private var lastEaten: MutableMap<UUID, LastEaten>? = null
    private var cooldown: Cooldown? = null

    private var normalCooldownMessage: String? = null
    private var enchantedCooldownMessage: String? = null

    init {
        instance = this
    }

    override fun reload() {
        normalCooldownMessage = module().getString("cooldown.message-normal")
        enchantedCooldownMessage = module().getString("cooldown.message-enchanted")

        cooldown = Cooldown(
            module().getLong("cooldown.normal"),
            module().getLong("cooldown.enchanted"),
            module().getBoolean("cooldown.is-shared")
        )
        lastEaten = WeakHashMap()

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
        // This should never ever ever ever run. If it does then you probably screwed something up.


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

        // Check if the cooldown has expired yet
        lastEaten!!.putIfAbsent(uuid, LastEaten())

        // If on cooldown send appropriate cooldown message
        if (cooldown!!.isOnCooldown(originalItem, lastEaten!![uuid]!!)) {
            val le = lastEaten!![uuid]

            val baseCooldown: Long
            var current: Instant?
            val message: String?

            if (consumedMaterial == Material.GOLDEN_APPLE) {
                baseCooldown = cooldown!!.normal
                current = le!!.lastNormalEaten
                message = normalCooldownMessage
            } else {
                baseCooldown = cooldown!!.enchanted
                current = le!!.lastEnchantedEaten
                message = enchantedCooldownMessage
            }

            val newestEatTime = le.newestEatTime
            if (cooldown!!.sharedCooldown && newestEatTime.isPresent) current = newestEatTime.get()

            val seconds = baseCooldown - (Instant.now().epochSecond - current!!.epochSecond)

            if (!message.isNullOrEmpty()) send(
                player, message.replace("%seconds%".toRegex(), seconds.toString())
            )

            e.isCancelled = true
            return
        }

        lastEaten!![uuid]!!.setForItem(originalItem)

        if (!isSettingEnabled("old-potion-effects")) return

        // Save player's current potion effects
        val previousPotionEffects = player.activePotionEffects

        val newEffects =
            if (ENCHANTED_GOLDEN_APPLE.isSame(originalItem)) enchantedGoldenAppleEffects else goldenAppleEffects
        val defaultEffects = if (ENCHANTED_GOLDEN_APPLE.isSame(originalItem)) nappleEffects else gappleEffects

        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // Remove all potion effects the apple added
            player.activePotionEffects.stream().map { obj: PotionEffect -> obj.type }
                .filter { o: PotionEffectType? -> defaultEffects.contains(o) }
                .forEach { type: PotionEffectType? -> player.removePotionEffect(type!!) }
            // Add previous potion effects from before eating the apple
            player.addPotionEffects(previousPotionEffects)
            // Add new custom effects from eating the apple
            applyEffects(player, newEffects!!)
        }, 1L)
    }


    private fun applyEffects(target: LivingEntity, newEffects: List<PotionEffect>) {
        for (newEffect in newEffects) {
            // Find the existing effect of the same type with the highest amplifier
            val highestExistingEffect =
                target.activePotionEffects.stream().filter { e: PotionEffect -> e.type === newEffect.type }
                    .max(Comparator.comparingInt { obj: PotionEffect -> obj.amplifier }).orElse(null)

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
        val appleEffects: MutableList<PotionEffect> = ArrayList()

        val sect = module().getConfigurationSection(path)
        for (key in sect!!.getKeys(false)) {
            val duration = sect.getInt("$key.duration") * 20 // Convert seconds to ticks
            val amplifier = sect.getInt("$key.amplifier")

            val type = fromNewName(key)
            Objects.requireNonNull(type, String.format("Invalid potion effect type '%s'!", key))

            val fx = PotionEffect(type!!, duration, amplifier)
            appleEffects.add(fx)
        }
        return appleEffects
    }

    @EventHandler
    fun onPlayerQuit(e: PlayerQuitEvent) {
        val uuid = e.player.uniqueId
        if (lastEaten != null) lastEaten!!.remove(uuid)
    }

    /**
     * Get player's current golden apple cooldown
     *
     * @param playerUUID The UUID of the player to check the cooldown for.
     * @return The remaining cooldown time in seconds, or 0 if there is no cooldown, or it has expired.
     */
    fun getGappleCooldown(playerUUID: UUID): Long {
        val lastEatenInfo = lastEaten!![playerUUID]
        if (lastEatenInfo?.lastNormalEaten != null) {
            val timeElapsedSinceEaten = Duration.between(lastEatenInfo.lastNormalEaten, Instant.now()).seconds
            val cooldownRemaining = cooldown!!.normal - timeElapsedSinceEaten
            return max(cooldownRemaining.toDouble(), 0.0).toLong() // Return 0 if the cooldown has expired
        }
        return 0
    }

    /**
     * Get player's current enchanted golden apple cooldown
     *
     * @param playerUUID The UUID of the player to check the cooldown for.
     * @return The remaining cooldown time in seconds, or 0 if there is no cooldown, or it has expired.
     */
    fun getNappleCooldown(playerUUID: UUID): Long {
        val lastEatenInfo = lastEaten!![playerUUID]
        if (lastEatenInfo?.lastEnchantedEaten != null) {
            val timeElapsedSinceEaten = Duration.between(lastEatenInfo.lastEnchantedEaten, Instant.now()).seconds
            val cooldownRemaining = cooldown!!.enchanted - timeElapsedSinceEaten
            return max(cooldownRemaining.toDouble(), 0.0).toLong() // Return 0 if the cooldown has expired
        }
        return 0
    }

    private class LastEaten {
        var lastNormalEaten: Instant? = null
        var lastEnchantedEaten: Instant? = null

        fun getForItem(item: ItemStack): Optional<Instant> {
            return if (ENCHANTED_GOLDEN_APPLE.isSame(item)) Optional.ofNullable<Instant>(lastEnchantedEaten)
            else Optional.ofNullable<Instant>(lastNormalEaten)
        }

        val newestEatTime: Optional<Instant>
            get() {
                if (lastEnchantedEaten == null) {
                    return Optional.ofNullable(lastNormalEaten)
                }
                if (lastNormalEaten == null) {
                    return Optional.of(lastEnchantedEaten!!)
                }
                return Optional.of(
                    (if (lastNormalEaten!! < lastEnchantedEaten) lastEnchantedEaten else lastNormalEaten)!!
                )
            }

        fun setForItem(item: ItemStack) {
            if (ENCHANTED_GOLDEN_APPLE.isSame(item)) {
                lastEnchantedEaten = Instant.now()
            } else {
                lastNormalEaten = Instant.now()
            }
        }
    }

    @JvmRecord
    private data class Cooldown(val normal: Long, val enchanted: Long, val sharedCooldown: Boolean) {
        fun getCooldownForItem(item: ItemStack): Long {
            return if (ENCHANTED_GOLDEN_APPLE.isSame(item)) enchanted else normal
        }

        fun isOnCooldown(item: ItemStack, lastEaten: LastEaten): Boolean {
            return (if (sharedCooldown) lastEaten.newestEatTime else lastEaten.getForItem(item)).map { it: Instant? ->
                ChronoUnit.SECONDS.between(
                    it, Instant.now()
                )
            }.map { it: Long -> it < getCooldownForItem(item) }.orElse(false)
        }
    }

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
            PotionEffectTypeCompat.RESISTANCE.get()
        )
        lateinit var instance: ModuleGoldenApple
    }
}
