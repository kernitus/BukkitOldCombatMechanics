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

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;


/**
 * Allows configurable potion effect durations.
 */
public class ModuleOldPotionEffects extends OCMModule {
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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionThrow(PlayerInteractEvent event) {
        final Player player = event.getPlayer();
        if (!isEnabled(player.getWorld())) return;

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

        final PotionData potionData = potionMeta.getBasePotionData();
        final PotionType potionType = potionData.getType();

        if (EXCLUDED_POTION_TYPES.contains(potionType)) return;

        final int duration = getPotionDuration(potionData, splash);
        int amplifier = potionData.isUpgraded() ? 1 : 0;
        if (potionType == PotionType.WEAKNESS) {
            // Set level to 0 so that it doesn't prevent the EntityDamageByEntityEvent from being called
            // due to damage being lower than 0 as some 1.9 weapons deal less damage
            amplifier = -1;
        }

        final PotionEffectType effectType = requireNonNull(potionType.getEffectType());
        potionMeta.addCustomEffect(new PotionEffect(effectType, duration, amplifier), false);
        potionMeta.setBasePotionData(new PotionData(PotionType.WATER));

        potionItem.setItemMeta(potionMeta);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(OCMEntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        if (!isEnabled(damager.getWorld())) return;

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

    private int getPotionDuration(PotionData potionData, boolean splash) {
        final PotionType potionType = potionData.getType();

        final GenericPotionDurations potionDurations = splash ? durations.get(potionType).getSplash()
                : durations.get(potionType).getDrinkable();

        int duration;
        if (potionData.isExtended()) duration = potionDurations.getExtendedTime();
        else if (potionData.isUpgraded()) duration = potionDurations.getIITime();
        else duration = potionDurations.getBaseTime();

        duration *= 20; // Convert seconds to ticks
        debug("Potion type: " + potionType.name() + " Duration: " + duration);

        return duration;
    }
}
