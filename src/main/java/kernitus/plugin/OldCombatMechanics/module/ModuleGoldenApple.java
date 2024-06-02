/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import com.google.common.collect.ImmutableSet;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffectTypeCompat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static kernitus.plugin.OldCombatMechanics.versions.materials.MaterialRegistry.ENCHANTED_GOLDEN_APPLE;

/**
 * Customise the golden apple effects.
 */
public class ModuleGoldenApple extends OCMModule {

    // Default apple effects
    // Gapple: absorption I, regen II
    private static final Set<PotionEffectType> gappleEffects = ImmutableSet.of(PotionEffectType.ABSORPTION,
            PotionEffectType.REGENERATION);
    // Napple: absorption IV, regen II, fire resistance I, resistance I
    private static final Set<PotionEffectType> nappleEffects = ImmutableSet.of(PotionEffectType.ABSORPTION,
            PotionEffectType.REGENERATION, PotionEffectType.FIRE_RESISTANCE, PotionEffectTypeCompat.RESISTANCE.get());
    private List<PotionEffect> enchantedGoldenAppleEffects, goldenAppleEffects;
    private ShapedRecipe enchantedAppleRecipe;

    private Map<UUID, LastEaten> lastEaten;
    private Cooldown cooldown;

    private String normalCooldownMessage, enchantedCooldownMessage;
    private static ModuleGoldenApple INSTANCE;

    public ModuleGoldenApple(OCMMain plugin) {
        super(plugin, "old-golden-apples");
        INSTANCE = this;
    }

    @SuppressWarnings("deprecated")
    @Override
    public void reload() {
        normalCooldownMessage = module().getString("cooldown.message-normal");
        enchantedCooldownMessage = module().getString("cooldown.message-enchanted");

        cooldown = new Cooldown(
                module().getLong("cooldown.normal"),
                module().getLong("cooldown.enchanted"),
                module().getBoolean("cooldown.is-shared")
        );
        lastEaten = new WeakHashMap<>();

        enchantedGoldenAppleEffects = getPotionEffects("enchanted-golden-apple-effects");
        goldenAppleEffects = getPotionEffects("golden-apple-effects");

        try {
            enchantedAppleRecipe = new ShapedRecipe(
                    new NamespacedKey(plugin, "MINECRAFT"),
                    ENCHANTED_GOLDEN_APPLE.newInstance()
            );
        } catch (NoClassDefFoundError e) {
            enchantedAppleRecipe = new ShapedRecipe(ENCHANTED_GOLDEN_APPLE.newInstance());
        }
        enchantedAppleRecipe
                .shape("ggg", "gag", "ggg")
                .setIngredient('g', Material.GOLD_BLOCK)
                .setIngredient('a', Material.APPLE);

        registerCrafting();
    }

    public static ModuleGoldenApple getInstance() {
        return ModuleGoldenApple.INSTANCE;
    }

    private void registerCrafting() {
        if (isEnabled() && module().getBoolean("enchanted-golden-apple-crafting")) {
            if (!Bukkit.getRecipesFor(ENCHANTED_GOLDEN_APPLE.newInstance()).isEmpty()) return;
            Bukkit.addRecipe(enchantedAppleRecipe);
            debug("Added napple recipe");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemCraft(PrepareItemCraftEvent e) {
        final ItemStack item = e.getInventory().getResult();
        if (item == null)
            return; // This should never ever ever ever run. If it does then you probably screwed something up.

        if (ENCHANTED_GOLDEN_APPLE.isSame(item)) {
            final HumanEntity player = e.getView().getPlayer();

            if (isSettingEnabled("no-conflict-mode")) return;

            if (!isEnabled(player) || !isSettingEnabled("enchanted-golden-apple-crafting"))
                e.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent e) {
        final Player player = e.getPlayer();

        if (!isEnabled(player)) return;

        final ItemStack originalItem = e.getItem();
        final Material consumedMaterial = originalItem.getType();

        if (consumedMaterial != Material.GOLDEN_APPLE &&
                !ENCHANTED_GOLDEN_APPLE.isSame(originalItem)) return;

        final UUID uuid = player.getUniqueId();

        // Check if the cooldown has expired yet
        lastEaten.putIfAbsent(uuid, new LastEaten());

        // If on cooldown send appropriate cooldown message
        if (cooldown.isOnCooldown(originalItem, lastEaten.get(uuid))) {
            final LastEaten le = lastEaten.get(uuid);

            final long baseCooldown;
            Instant current;
            final String message;

            if (consumedMaterial == Material.GOLDEN_APPLE) {
                baseCooldown = cooldown.normal;
                current = le.lastNormalEaten;
                message = normalCooldownMessage;
            } else {
                baseCooldown = cooldown.enchanted;
                current = le.lastEnchantedEaten;
                message = enchantedCooldownMessage;
            }

            final Optional<Instant> newestEatTime = le.getNewestEatTime();
            if (cooldown.sharedCooldown && newestEatTime.isPresent())
                current = newestEatTime.get();

            final long seconds = baseCooldown - (Instant.now().getEpochSecond() - current.getEpochSecond());

            if (message != null && !message.isEmpty())
                Messenger.send(player, message.replaceAll("%seconds%", String.valueOf(seconds)));

            e.setCancelled(true);
            return;
        }

        lastEaten.get(uuid).setForItem(originalItem);

        if (!isSettingEnabled("old-potion-effects")) return;

        // Save player's current potion effects
        final Collection<PotionEffect> previousPotionEffects = player.getActivePotionEffects();

        final List<PotionEffect> newEffects = ENCHANTED_GOLDEN_APPLE.isSame(originalItem) ?
                enchantedGoldenAppleEffects : goldenAppleEffects;
        final Set<PotionEffectType> defaultEffects = ENCHANTED_GOLDEN_APPLE.isSame(originalItem) ?
                nappleEffects : gappleEffects;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            // Remove all potion effects the apple added
            player.getActivePotionEffects().stream()
                    .map(PotionEffect::getType)
                    .filter(defaultEffects::contains)
                    .forEach(player::removePotionEffect);
            // Add previous potion effects from before eating the apple
            player.addPotionEffects(previousPotionEffects);
            // Add new custom effects from eating the apple
            applyEffects(player, newEffects);
        }, 1L);
    }


    private void applyEffects(LivingEntity target, List<PotionEffect> newEffects) {
        for (PotionEffect newEffect : newEffects) {
            // Find the existing effect of the same type with the highest amplifier
            final PotionEffect highestExistingEffect = target.getActivePotionEffects().stream()
                    .filter(e -> e.getType() == newEffect.getType())
                    .max(Comparator.comparingInt(PotionEffect::getAmplifier))
                    .orElse(null);

            if (highestExistingEffect != null) {
                // If the new effect has a higher amplifier, apply it
                if (newEffect.getAmplifier() > highestExistingEffect.getAmplifier()) {
                    target.addPotionEffect(newEffect);
                }
                // If the amplifiers are the same and the new effect has a longer duration, refresh the duration
                else if (newEffect.getAmplifier() == highestExistingEffect.getAmplifier() &&
                        newEffect.getDuration() > highestExistingEffect.getDuration()) {
                    target.addPotionEffect(newEffect);
                }
                // If the new effect has a lower amplifier or shorter/equal duration, do nothing
            } else {
                // If there is no existing effect of the same type, apply the new effect
                target.addPotionEffect(newEffect);
            }
        }
    }


    private List<PotionEffect> getPotionEffects(String path) {
        final List<PotionEffect> appleEffects = new ArrayList<>();

        final ConfigurationSection sect = module().getConfigurationSection(path);
        for (String key : sect.getKeys(false)) {
            final int duration = sect.getInt(key + ".duration") * 20; // Convert seconds to ticks
            final int amplifier = sect.getInt(key + ".amplifier");

            final PotionEffectType type = PotionEffectTypeCompat.fromNewName(key);
            Objects.requireNonNull(type, String.format("Invalid potion effect type '%s'!", key));

            final PotionEffect fx = new PotionEffect(type, duration, amplifier);
            appleEffects.add(fx);
        }
        return appleEffects;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        final UUID uuid = e.getPlayer().getUniqueId();
        if (lastEaten != null) lastEaten.remove(uuid);
    }

    /**
     * Get player's current golden apple cooldown
     *
     * @param playerUUID The UUID of the player to check the cooldown for.
     * @return The remaining cooldown time in seconds, or 0 if there is no cooldown, or it has expired.
     */
    public long getGappleCooldown(UUID playerUUID) {
        final LastEaten lastEatenInfo = lastEaten.get(playerUUID);
        if (lastEatenInfo != null && lastEatenInfo.lastNormalEaten != null) {
            long timeElapsedSinceEaten = Duration.between(lastEatenInfo.lastNormalEaten, Instant.now()).getSeconds();
            long cooldownRemaining = cooldown.normal - timeElapsedSinceEaten;
            return Math.max(cooldownRemaining, 0); // Return 0 if the cooldown has expired
        }
        return 0;
    }

    /**
     * Get player's current enchanted golden apple cooldown
     *
     * @param playerUUID The UUID of the player to check the cooldown for.
     * @return The remaining cooldown time in seconds, or 0 if there is no cooldown, or it has expired.
     */
    public long getNappleCooldown(UUID playerUUID) {
        final LastEaten lastEatenInfo = lastEaten.get(playerUUID);
        if (lastEatenInfo != null && lastEatenInfo.lastEnchantedEaten != null) {
            long timeElapsedSinceEaten = Duration.between(lastEatenInfo.lastEnchantedEaten, Instant.now()).getSeconds();
            long cooldownRemaining = cooldown.enchanted - timeElapsedSinceEaten;
            return Math.max(cooldownRemaining, 0); // Return 0 if the cooldown has expired
        }
        return 0;
    }

    private static class LastEaten {
        private Instant lastNormalEaten;
        private Instant lastEnchantedEaten;

        private Optional<Instant> getForItem(ItemStack item) {
            return ENCHANTED_GOLDEN_APPLE.isSame(item)
                    ? Optional.ofNullable(lastEnchantedEaten)
                    : Optional.ofNullable(lastNormalEaten);
        }

        private Optional<Instant> getNewestEatTime() {
            if (lastEnchantedEaten == null) {
                return Optional.ofNullable(lastNormalEaten);
            }
            if (lastNormalEaten == null) {
                return Optional.of(lastEnchantedEaten);
            }
            return Optional.of(
                    lastNormalEaten.compareTo(lastEnchantedEaten) < 0 ? lastEnchantedEaten : lastNormalEaten
            );
        }

        private void setForItem(ItemStack item) {
            if (ENCHANTED_GOLDEN_APPLE.isSame(item)) {
                lastEnchantedEaten = Instant.now();
            } else {
                lastNormalEaten = Instant.now();
            }
        }
    }

    private record Cooldown(long normal, long enchanted, boolean sharedCooldown) {
        private long getCooldownForItem(ItemStack item) {
            return ENCHANTED_GOLDEN_APPLE.isSame(item) ? enchanted : normal;
        }

        boolean isOnCooldown(ItemStack item, LastEaten lastEaten) {
            return (sharedCooldown ? lastEaten.getNewestEatTime() : lastEaten.getForItem(item))
                    .map(it -> ChronoUnit.SECONDS.between(it, Instant.now()))
                    .map(it -> it < getCooldownForItem(item))
                    .orElse(false);
        }
    }
}
