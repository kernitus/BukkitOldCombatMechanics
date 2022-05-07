/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import kernitus.plugin.OldCombatMechanics.utilities.potions.GenericPotionDurations;
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionDurations;
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffects;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;


/**
 * Allows configurable potion effect durations.
 */
public class ModuleOldPotionEffects extends Module {

    private static final Set<PotionType> EXCLUDED_POTION_TYPES = EnumSet.of(
            // This only includes 1.9 potions, others are added later for compatibility
            // Instant potions have no duration that can be modified
            PotionType.INSTANT_DAMAGE, PotionType.INSTANT_HEAL,
            // Base potions without any effect
            PotionType.AWKWARD, PotionType.MUNDANE, PotionType.THICK, PotionType.UNCRAFTABLE, PotionType.WATER
    );

    private Map<PotionType, PotionDurations> durations;

    public ModuleOldPotionEffects(OCMMain plugin) {
        super(plugin, "old-potion-effects");

        try {
            //Turtle Master potion has two effects and Bukkit only returns one with #getEffectType()
            EXCLUDED_POTION_TYPES.add(PotionType.TURTLE_MASTER);
        } catch (NoSuchFieldError e) {
            debug("Skipping excluding a potion (probably older server version)");
        }

        reload();
    }

    @Override
    public void reload() {
        durations = ConfigUtils.loadPotionDurationsList(module());
    }

    /**
     * Change the duration using values defined in config for drinking potions
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPlayerDrinksPotion(PlayerItemConsumeEvent event) {
        final Player player = event.getPlayer();
        if (!isEnabled(player.getWorld())) return;

        final ItemStack potionItem = event.getItem();
        if (potionItem.getType() != Material.POTION) return;

        final PotionMeta potionMeta = (PotionMeta) potionItem.getItemMeta();
        if (potionMeta == null) return;

        final PotionData potionData = potionMeta.getBasePotionData();
        final PotionType potionType = potionData.getType();

        if (EXCLUDED_POTION_TYPES.contains(potionType)) return;

        event.setCancelled(true);

        final int amplifier = potionData.isUpgraded() ? 1 : 0;
        final int duration = getPotionDuration(potionData, false);

        final PotionEffectType effectType = requireNonNull(potionType.getEffectType());
        final PotionEffect potionEffect = new PotionEffect(effectType, duration, amplifier);

        setNewPotionEffect(player, potionEffect);

        // Remove item from hand since we cancelled the event
        if (player.getGameMode() == GameMode.CREATIVE) return;

        final PlayerInventory playerInventory = player.getInventory();

        final int amount = potionItem.getAmount();
        ItemStack toSet = new ItemStack(Material.GLASS_BOTTLE);

        final boolean isInMainHand = potionItem.equals(playerInventory.getItemInMainHand());

        // There was more than one potion in the stack
        if (amount > 1) {
            playerInventory.addItem(toSet);
            toSet = potionItem;
            toSet.setAmount(amount - 1);
        }

        // If potion was in main hand
        if (isInMainHand) playerInventory.setItemInMainHand(toSet);
        else playerInventory.setItemInOffHand(toSet);
    }

    /**
     * Change the duration using values defined in config for splash potions
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!isEnabled(event.getEntity().getWorld())) return;

        final ThrownPotion thrownPotion = event.getPotion();
        final PotionMeta potionMeta = (PotionMeta) thrownPotion.getItem().getItemMeta();

        for (PotionEffect potionEffect : thrownPotion.getEffects()) {
            final PotionData potionData = potionMeta.getBasePotionData();

            if (EXCLUDED_POTION_TYPES.contains(potionData.getType())) return;

            event.setCancelled(true);

            final int duration = getPotionDuration(potionData, true);

            final PotionEffect newEffect = new PotionEffect(potionEffect.getType(), duration, potionEffect.getAmplifier());

            event.getAffectedEntities().forEach(livingEntity -> setNewPotionEffect(livingEntity, newEffect));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(OCMEntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        if (!isEnabled(damager.getWorld())) return;

        final double weaknessModifier = event.getWeaknessModifier();

        if (weaknessModifier != 0) {
            event.setIsWeaknessModifierMultiplier(module().getBoolean("weakness.multiplier"));
            final double newWeaknessModifier = module().getDouble("weakness.modifier");
            event.setWeaknessModifier(newWeaknessModifier);
            debug("Old weakness modifier: " + weaknessModifier + " New: " + newWeaknessModifier, damager);
        }

        final double strengthModifier = event.getStrengthModifier();

        if (strengthModifier != 0) {
            event.setIsStrengthModifierMultiplier(module().getBoolean("strength.multiplier"));
            event.setIsStrengthModifierAddend(module().getBoolean("strength.addend"));
            final double newStrengthModifier = module().getDouble("strength.modifier");
            event.setStrengthModifier(newStrengthModifier);
            debug("Old strength modifier: " + strengthModifier + " New: " + newStrengthModifier, damager);
        }
    }

    private void setNewPotionEffect(LivingEntity livingEntity, PotionEffect potionEffect) {
        if (!livingEntity.hasPotionEffect(potionEffect.getType())) {
            livingEntity.addPotionEffect(potionEffect);
            return;
        }

        final PotionEffect activeEffect = PotionEffects.getOrNull(livingEntity, potionEffect.getType());
        final int remainingDuration = activeEffect.getDuration();

        // If new effect is type II while old wasn't, or new would last longer than
        // remaining time but isn't a level downgrade (eg II -> I), set it
        final int newAmplifier = potionEffect.getAmplifier();
        final int activeAmplifier = activeEffect.getAmplifier();

        if (newAmplifier < activeAmplifier) return;

        if (newAmplifier > activeAmplifier || remainingDuration < potionEffect.getDuration())
            livingEntity.addPotionEffect(potionEffect, true);

    }

    private int getPotionDuration(PotionData potionData, boolean splash) {
        final PotionType potionType = potionData.getType();
        debug("Potion type: " + potionType.name());

        final GenericPotionDurations potionDurations = splash ? durations.get(potionType).getSplash() : durations.get(potionType).getDrinkable();

        int duration;
        if (potionData.isExtended()) duration = potionDurations.getExtendedTime();
        else if (potionData.isUpgraded()) duration = potionDurations.getIITime();
        else duration = potionDurations.getBaseTime();

        return duration * 20; // Convert seconds to ticks
    }
}
