/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionDurations;
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionTypeCompat;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Allows configurable potion effect durations.
 */
public class ModuleOldPotionEffects extends OCMModule {
    private static final Set<PotionTypeCompat> EXCLUDED_POTION_TYPES = Set.of(
            // Base potions without any effect
            new PotionTypeCompat("AWKWARD"),
            new PotionTypeCompat("MUNDANE"),
            new PotionTypeCompat("THICK"),
            new PotionTypeCompat("WATER"),
            // Instant potions with no further effects
            new PotionTypeCompat("HARMING"),
            new PotionTypeCompat("STRONG_HARMING"),
            new PotionTypeCompat("HEALING"),
            new PotionTypeCompat("STRONG_HEALING"),
            // This type doesn't exist anymore >1.20.5, is specially handled in compat class
            new PotionTypeCompat("UNCRAFTABLE")
    );

    private Map<PotionTypeCompat, PotionDurations> durations;

    public ModuleOldPotionEffects(OCMMain plugin) {
        super(plugin, "old-potion-effects");

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
        if (!isEnabled(player)) return;

        final ItemStack potionItem = event.getItem();
        if (potionItem.getType() != Material.POTION) return;

        adjustPotion(potionItem, false);
        event.setItem(potionItem);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionDispense(BlockDispenseEvent event) {
        if (!isEnabled(event.getBlock().getWorld())) return;

        final ItemStack item = event.getItem();
        final Material material = item.getType();

        if (material == Material.SPLASH_POTION || material == Material.LINGERING_POTION)
            adjustPotion(item, true);
    }

    // We change the potion on-the-fly just as it's thrown to be able to change the effect
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionThrow(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (!isEnabled(player)) return;

        final Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        final ItemStack item = event.getItem();
        if (item == null) return;

        final Material material = item.getType();
        if (material == Material.SPLASH_POTION || material == Material.LINGERING_POTION)
            adjustPotion(item, true);
    }

    /**
     * Sets custom potion duration and effects
     *
     * @param potionItem The potion item with adjusted duration and effects
     */
    private void adjustPotion(ItemStack potionItem, boolean splash) {
        final PotionMeta potionMeta = (PotionMeta) potionItem.getItemMeta();
        if (potionMeta == null) return;

        final PotionTypeCompat potionTypeCompat = PotionTypeCompat.fromPotionMeta(potionMeta);

        if (EXCLUDED_POTION_TYPES.contains(potionTypeCompat)) return;

        final Integer duration = getPotionDuration(potionTypeCompat, splash);
        if (duration == null) {
            debug("Potion type " + potionTypeCompat.getNewName() + " not found in config, leaving as is...");
            return;
        }

        int amplifier = potionTypeCompat.isStrong() ? 1 : 0;

        if (potionTypeCompat.equals(new PotionTypeCompat("WEAKNESS"))) {
            // Set level to 0 so that it doesn't prevent the EntityDamageByEntityEvent from being called
            // due to damage being lower than 0 as some 1.9 weapons deal less damage
            amplifier = -1;
        }

        List<PotionEffectType> potionEffects;
        final PotionType potionType = potionTypeCompat.getType();
        try {
            potionEffects = potionType.getPotionEffects().stream().map(PotionEffect::getType).toList();
        } catch (NoSuchMethodError e) {
            potionEffects = List.of(potionType.getEffectType());
        }

        for (PotionEffectType effectType : potionEffects) {
            potionMeta.addCustomEffect(new PotionEffect(effectType, duration, amplifier), false);
        }

        try { // For >=1.20
            potionMeta.setBasePotionType(PotionType.WATER);
        } catch (NoSuchMethodError e) {
            potionMeta.setBasePotionData(new PotionData(PotionType.WATER));
        }

        potionItem.setItemMeta(potionMeta);
    }


    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(OCMEntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        if (!isEnabled(damager, event.getDamagee())) return;

        if (event.hasWeakness()) {
            event.setIsWeaknessModifierMultiplier(module().getBoolean("weakness.multiplier"));
            final double newWeaknessModifier = module().getDouble("weakness.modifier");
            event.setWeaknessModifier(newWeaknessModifier);
            event.setWeaknessLevel(1);
            debug("Old weakness modifier: " + event.getWeaknessLevel() +
                    " New: " + newWeaknessModifier, damager);
        }

        final double strengthModifier = event.getStrengthModifier();

        if (strengthModifier > 0) {
            event.setIsStrengthModifierMultiplier(module().getBoolean("strength.multiplier"));
            event.setIsStrengthModifierAddend(module().getBoolean("strength.addend"));
            final double newStrengthModifier = module().getDouble("strength.modifier");
            event.setStrengthModifier(newStrengthModifier);
            debug("Old strength modifier: " + strengthModifier + " New: " + newStrengthModifier, damager);
        }
    }

    private Integer getPotionDuration(PotionTypeCompat potionTypeCompat, boolean splash) {
        final PotionDurations potionDurations = durations.get(potionTypeCompat);
        if (potionDurations == null) return null;
        final int duration = splash ? potionDurations.splash() : potionDurations.drinkable();

        debug("Potion type: " + potionTypeCompat.getNewName() + " Duration: " + duration + " ticks");

        return duration;
    }
}
