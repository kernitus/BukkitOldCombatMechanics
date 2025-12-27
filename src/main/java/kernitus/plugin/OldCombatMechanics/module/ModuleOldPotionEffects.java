/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionDurations;
import com.cryptomorin.xseries.XPotion;
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionKey;
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffects;
import kernitus.plugin.OldCombatMechanics.utilities.potions.WeaknessCompensation;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Allows configurable potion effect durations.
 */
public class ModuleOldPotionEffects extends OCMModule {
    private static final Set<String> NON_EFFECT_POTION_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "AWKWARD",
                    "MUNDANE",
                    "THICK",
                    "WATER",
                    "UNCRAFTABLE",
                    "HARMING",
                    "STRONG_HARMING",
                    "HEALING",
                    "STRONG_HEALING",
                    "INSTANT_DAMAGE",
                    "INSTANT_HEAL",
                    "INSTANT_HEALTH"
            )));

    private Map<PotionKey, PotionDurations> durations;
    private final Set<String> warnedUnknownPotionTypes = new HashSet<>();
    private boolean weaknessAmplifierClamped;

    public ModuleOldPotionEffects(OCMMain plugin) {
        super(plugin, "old-potion-effects");

        reload();
    }

    @Override
    public void reload() {
        durations = ConfigUtils.loadPotionDurationsList(module());
        weaknessAmplifierClamped = detectWeaknessAmplifierClamp();
        syncWeaknessCompensation();
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

    @Override
    public void onModesetChange(Player player) {
        if (!weaknessAmplifierClamped) {
            WeaknessCompensation.remove(player);
            return;
        }
        applyWeaknessCompensation(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPotionEffect(EntityPotionEffectEvent event) {
        if (!weaknessAmplifierClamped) return;

        final Entity entity = event.getEntity();
        if (!(entity instanceof LivingEntity)) return;
        final LivingEntity livingEntity = (LivingEntity) entity;

        if (!isEnabled(livingEntity)) {
            WeaknessCompensation.remove(livingEntity);
            return;
        }

        final PotionEffect newEffect = event.getNewEffect();
        final PotionEffect oldEffect = event.getOldEffect();
        final PotionEffectType type = newEffect != null ? newEffect.getType() :
                (oldEffect != null ? oldEffect.getType() : null);
        final PotionEffectType weakness = XPotion.WEAKNESS.get();
        if (type == null || weakness == null || !type.equals(weakness)) return;

        if (newEffect != null && newEffect.getAmplifier() == 0) {
            WeaknessCompensation.apply(livingEntity);
        } else {
            WeaknessCompensation.remove(livingEntity);
        }
    }

    /**
     * Sets custom potion duration and effects
     *
     * @param potionItem The potion item with adjusted duration and effects
     */
    private void adjustPotion(ItemStack potionItem, boolean splash) {
        final PotionMeta potionMeta = (PotionMeta) potionItem.getItemMeta();
        if (potionMeta == null) return;

        PotionType potionType;
        String potionTypeName;
        try {
            potionType = potionMeta.getBasePotionType();
            if (potionType == null) return;
            potionTypeName = potionType.name();
        } catch (NoSuchMethodError e) {
            potionType = potionMeta.getBasePotionData().getType();
            potionTypeName = potionType.name();
        }

        final PotionKey potionKey = PotionKey.fromPotionMeta(potionMeta).orElse(null);
        if (potionKey == null) {
            if (!NON_EFFECT_POTION_TYPES.contains(potionTypeName) && warnedUnknownPotionTypes.add(potionTypeName)) {
                Messenger.warn("[%s] Unknown potion type '%s' encountered; old-potion-effects will not adjust it",
                        getModuleName(), potionTypeName);
            }
            return;
        }

        final Integer duration = getPotionDuration(potionKey, splash);
        if (duration == null) {
            debug("Potion type " + potionKey.getDebugName() + " not found in config, leaving as is...");
            return;
        }

        int amplifier = potionKey.isStrong() ? 1 : 0;

        if (potionKey.isPotion(XPotion.WEAKNESS)) {
            // Set level to 0 so that it doesn't prevent the EntityDamageByEntityEvent from being called
            // due to damage being lower than 0 as some 1.9 weapons deal less damage
            amplifier = -1;
        }

        List<PotionEffectType> potionEffects;
        try {
            potionEffects = potionType.getPotionEffects().stream()
                    .map(PotionEffect::getType)
                    .collect(Collectors.toList());
        } catch (NoSuchMethodError e) {
            potionEffects = Collections.singletonList(potionType.getEffectType());
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

    private Integer getPotionDuration(PotionKey potionKey, boolean splash) {
        final PotionDurations potionDurations = durations.get(potionKey);
        if (potionDurations == null) return null;
        final int duration = splash ? potionDurations.splash() : potionDurations.drinkable();

        debug("Potion type: " + potionKey.getDebugName() + " Duration: " + duration + " ticks");

        return duration;
    }

    private boolean detectWeaknessAmplifierClamp() {
        try {
            final PotionEffectType weakness = XPotion.WEAKNESS.get();
            if (weakness == null) return false;
            final ItemStack item = new ItemStack(Material.POTION);
            final PotionMeta meta = (PotionMeta) item.getItemMeta();
            if (meta == null) return false;
            meta.addCustomEffect(new PotionEffect(weakness, 20, -1), true);
            item.setItemMeta(meta);
            final PotionMeta updated = (PotionMeta) item.getItemMeta();
            if (updated == null) return false;
            final PotionEffect effect = updated.getCustomEffects().stream()
                    .filter(potionEffect -> potionEffect.getType().equals(weakness))
                    .findFirst()
                    .orElse(null);
            return effect != null && effect.getAmplifier() != -1;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void syncWeaknessCompensation() {
        if (!weaknessAmplifierClamped) {
            removeWeaknessCompensation();
            return;
        }

        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                applyWeaknessCompensation(entity);
            }
        }
    }

    private void removeWeaknessCompensation() {
        for (World world : plugin.getServer().getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                WeaknessCompensation.remove(entity);
            }
        }
    }

    private void applyWeaknessCompensation(LivingEntity entity) {
        if (!isEnabled(entity)) {
            WeaknessCompensation.remove(entity);
            return;
        }
        final PotionEffectType weakness = XPotion.WEAKNESS.get();
        if (weakness == null) return;
        final PotionEffect effect = PotionEffects.getOrNull(entity, weakness);
        if (effect != null && effect.getAmplifier() == 0) {
            WeaknessCompensation.apply(entity);
        } else {
            WeaknessCompensation.remove(entity);
        }
    }
}
