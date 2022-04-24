/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.*;
import org.bukkit.advancement.Advancement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerStatisticIncrementEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static kernitus.plugin.OldCombatMechanics.versions.materials.MaterialRegistry.ENCHANTED_GOLDEN_APPLE;

/**
 * Customise the golden apple effects.
 */
public class ModuleGoldenApple extends Module {

    private List<PotionEffect> enchantedGoldenAppleEffects, goldenAppleEffects;
    private ShapedRecipe enchantedAppleRecipe;

    private Map<UUID, LastEaten> lastEaten;
    private Cooldown cooldown;

    private String normalCooldownMessage, enchantedCooldownMessage;

    public ModuleGoldenApple(OCMMain plugin) {
        super(plugin, "old-golden-apples");
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

        enchantedGoldenAppleEffects = getPotionEffects("napple");
        goldenAppleEffects = getPotionEffects("gapple");

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

    private void registerCrafting() {
        if (isEnabled() && module().getBoolean("enchanted-golden-apple-crafting")) {
            if (Bukkit.getRecipesFor(ENCHANTED_GOLDEN_APPLE.newInstance()).size() > 0) return;
            Bukkit.addRecipe(enchantedAppleRecipe);
            Messenger.debug("Added napple recipe");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareItemCraft(PrepareItemCraftEvent e) {
        ItemStack item = e.getInventory().getResult();
        if (item == null)
            return; // This should never ever ever ever run. If it does then you probably screwed something up.

        if (ENCHANTED_GOLDEN_APPLE.isSame(item)) {

            World world = e.getView().getPlayer().getWorld();

            if (isSettingEnabled("no-conflict-mode")) return;

            if (!isEnabled(world))
                e.getInventory().setResult(null);
            else if (isEnabled(world) && !isSettingEnabled("enchanted-golden-apple-crafting"))
                e.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemConsume(PlayerItemConsumeEvent e) {
        final Player p = e.getPlayer();

        if (!isEnabled(p.getWorld())) return;

        ItemStack item = e.getItem();
        final Material consumedMaterial = item.getType();

        if (consumedMaterial != Material.GOLDEN_APPLE &&
                !ENCHANTED_GOLDEN_APPLE.isSame(e.getItem())) return;

        final UUID uuid = p.getUniqueId();

        // Check if the cooldown has expired yet
        lastEaten.putIfAbsent(uuid, new LastEaten());

        // If on cooldown send appropriate cooldown message
        if (cooldown.isOnCooldown(item, lastEaten.get(uuid))) {
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
                Messenger.sendNormalMessage(p, message.replaceAll("%seconds%", String.valueOf(seconds)));

            e.setCancelled(true);
            return;
        }

        lastEaten.get(p.getUniqueId()).setForItem(item);

        if (!isSettingEnabled("old-potion-effects")) return;
        e.setCancelled(true);

        final ItemStack originalItem = e.getItem();
        final PlayerInventory inv = p.getInventory();

        // Hunger level
        int foodLevel = Math.min(p.getFoodLevel() + 4, 20);
        p.setFoodLevel(foodLevel);

        if (p.getGameMode() != GameMode.CREATIVE) item.setAmount(item.getAmount() - 1);

        // Gapple and Napple saturation is 9.6
        float saturation = p.getSaturation() + 9.6f;
        // "The total saturation never gets higher than the total number of hunger points"
        if (saturation > foodLevel)
            saturation = foodLevel;

        p.setSaturation(saturation);

        if (ENCHANTED_GOLDEN_APPLE.isSame(item)) applyEffects(p, enchantedGoldenAppleEffects);
        else applyEffects(p, goldenAppleEffects);

        if (item.getAmount() <= 0) item = null;

        ItemStack mainHand = inv.getItemInMainHand();
        ItemStack offHand = inv.getItemInOffHand();

        if (mainHand.equals(originalItem)) inv.setItemInMainHand(item);
        else if (offHand.equals(originalItem)) inv.setItemInOffHand(item);
        else if (mainHand.getType() == Material.GOLDEN_APPLE || ENCHANTED_GOLDEN_APPLE.isSame(mainHand))
            inv.setItemInMainHand(item);
        // The bug occurs here, so we must check which hand has the apples
        // A player can't eat food in the offhand if there is any in the main hand
        // On this principle if there are gapples in the mainhand it must be that one, else it's the offhand

        // Below here are fixes for statistics & advancements, given we are cancelling the event
        int initialValue = p.getStatistic(Statistic.USE_ITEM, consumedMaterial);

        // We need to increment player statistics for having eaten a gapple
        p.incrementStatistic(Statistic.USE_ITEM, consumedMaterial);

        // Call the event as .incrementStatistic doesn't seem to, and other plugins may rely on it
        PlayerStatisticIncrementEvent psie = new PlayerStatisticIncrementEvent(p, Statistic.USE_ITEM, initialValue, initialValue + 1, consumedMaterial);
        Bukkit.getServer().getPluginManager().callEvent(psie);

        try {
            NamespacedKey nsk = NamespacedKey.minecraft("husbandry/balanced_diet");
            Advancement advancement = Bukkit.getAdvancement(nsk);

            // Award advancement criterion for having eaten gapple, as incrementing statistic or calling event doesn't seem to
            if (advancement != null)
                p.getAdvancementProgress(advancement).awardCriteria(consumedMaterial.name().toLowerCase());
        } catch (NoClassDefFoundError ignored) {
        } // Pre 1.12 does not have advancements
    }

    private List<PotionEffect> getPotionEffects(String apple) {
        List<PotionEffect> appleEffects = new ArrayList<>();

        ConfigurationSection sect = module().getConfigurationSection(apple + "-effects");
        for (String key : sect.getKeys(false)) {
            int duration = sect.getInt(key + ".duration");
            int amplifier = sect.getInt(key + ".amplifier");

            PotionEffectType type = PotionEffectType.getByName(key);
            Objects.requireNonNull(type, String.format("Invalid potion effect type '%s'!", key));

            PotionEffect fx = new PotionEffect(type, duration, amplifier);
            appleEffects.add(fx);
        }
        return appleEffects;
    }

    private void applyEffects(LivingEntity target, List<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            OptionalInt maxActiveAmplifier = target.getActivePotionEffects().stream()
                    .filter(potionEffect -> potionEffect.getType() == effect.getType())
                    .mapToInt(PotionEffect::getAmplifier)
                    .max();

            // the active one is stronger, so do not apply the weaker one
            if (maxActiveAmplifier.orElse(-1) > effect.getAmplifier()) continue;

            // remove it, as the active one is weaker
            maxActiveAmplifier.ifPresent(ignored -> target.removePotionEffect(effect.getType()));

            target.addPotionEffect(effect);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (lastEaten != null) lastEaten.remove(e.getPlayer().getUniqueId());
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

    private static class Cooldown {
        private final long normal;
        private final long enchanted;
        private final boolean sharedCooldown;

        Cooldown(long normal, long enchanted, boolean sharedCooldown) {
            this.normal = normal;
            this.enchanted = enchanted;
            this.sharedCooldown = sharedCooldown;
        }

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
