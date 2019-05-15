package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.ConfigUtils;
import gvlfm78.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import gvlfm78.plugin.OldCombatMechanics.utilities.potions.GenericPotionDurations;
import gvlfm78.plugin.OldCombatMechanics.utilities.potions.PotionDurations;
import gvlfm78.plugin.OldCombatMechanics.utilities.potions.PotionEffects;
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
import java.util.HashMap;
import java.util.Set;

/**
 * Allows configurable potion effect durations.
 */
public class ModuleOldPotionEffects extends Module {

    private static final Set<PotionType> EXCLUDED_POTION_TYPES = EnumSet.of(
            // TODO: Why exclude this? Yea, it wasn't in 1.8, but still
            PotionType.LUCK,
            // instant potions have no duration that could be modified
            PotionType.INSTANT_DAMAGE, PotionType.INSTANT_HEAL,
            // base potions without any effect
            PotionType.AWKWARD, PotionType.MUNDANE, PotionType.THICK, PotionType.UNCRAFTABLE, PotionType.WATER
    );


    private HashMap<PotionType, PotionDurations> durations;

    public ModuleOldPotionEffects(OCMMain plugin){
        super(plugin, "old-potion-effects");
        reload();
    }

    @Override
    public void reload(){
        durations = ConfigUtils.loadPotionDurationsList(module());
    }

    /**
     * Change the duration using values defined in config
     * for drinking potions
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDrinksPotion(PlayerItemConsumeEvent event){
        ItemStack potionItem = event.getItem();
        if(potionItem.getType() != Material.POTION) return;

        PotionMeta potionMeta = (PotionMeta) potionItem.getItemMeta();
        PotionData potionData = potionMeta.getBasePotionData();
        PotionType potionType = potionData.getType();

        if(EXCLUDED_POTION_TYPES.contains(potionType)) return;

        event.setCancelled(true);

        int duration = getPotionDuration(potionData, false);

        PotionEffectType effectType = potionType.getEffectType();

        int amplifier = potionData.isUpgraded() ? 1 : 0;

        PotionEffect potionEffect = new PotionEffect(effectType, duration, amplifier);

        Player player = event.getPlayer();
        setNewPotionEffect(player, potionEffect);

        // Remove item from hand since we cancelled the event
        if(player.getGameMode() != GameMode.SURVIVAL) return;

        PlayerInventory playerInventory = player.getInventory();

        ItemStack glassBottle = new ItemStack(Material.GLASS_BOTTLE);
        int amount = potionItem.getAmount();

        if(amount > 1){
            potionItem.setAmount(amount - 1);
            player.getInventory().addItem(glassBottle);
        } else {
            // If it was just one potion set item to glass bottle
            if (potionItem.equals(playerInventory.getItemInMainHand()))
                playerInventory.setItemInMainHand(glassBottle);
            else
                playerInventory.setItemInOffHand(glassBottle);
        }
    }

    /**
     * Change the duration using values defined in config
     * for splash potions
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionSplash(PotionSplashEvent event){
        ThrownPotion thrownPotion = event.getPotion();
        PotionMeta potionMeta = (PotionMeta) thrownPotion.getItem().getItemMeta();

        for(PotionEffect potionEffect : thrownPotion.getEffects()){
            PotionData potionData = potionMeta.getBasePotionData();

            if(EXCLUDED_POTION_TYPES.contains(potionData.getType())) return;

            event.setCancelled(true);

            int duration = getPotionDuration(potionData, true);

            PotionEffect newEffect = new PotionEffect(potionEffect.getType(), duration, potionEffect.getAmplifier());

            event.getAffectedEntities()
                    .forEach(livingEntity -> setNewPotionEffect(livingEntity, newEffect));
        }
    }

    private void setNewPotionEffect(LivingEntity livingEntity, PotionEffect potionEffect){
        if(livingEntity.hasPotionEffect(potionEffect.getType())){
            PotionEffect activeEffect = PotionEffects.getOrNull(livingEntity, potionEffect.getType());

            int remainingDuration = activeEffect.getDuration();

            // If new effect it type II while old wasn't, or
            // new would last longer than remaining time but isn't a level downgrade (eg II -> I), set it
            int newAmplifier = potionEffect.getAmplifier();
            int activeAmplifier = activeEffect.getAmplifier();

            if(newAmplifier < activeAmplifier)
                return;

            if(newAmplifier > activeAmplifier || remainingDuration < potionEffect.getDuration()){
                livingEntity.addPotionEffect(potionEffect, true);
            }
        } else {
            livingEntity.addPotionEffect(potionEffect, false);
        }
    }

    private int getPotionDuration(PotionData potionData, boolean splash){
        PotionType potionType = potionData.getType();
        debug("Potion type: " + potionType.name());

        GenericPotionDurations potionDurations;

        if(splash) potionDurations = durations.get(potionType).getSplash();
        else potionDurations = durations.get(potionType).getDrinkable();

        int duration;
        if(potionData.isExtended()) duration = potionDurations.getExtendedTime();
        else if(potionData.isUpgraded()) duration = potionDurations.getIITime();
        else duration = potionDurations.getBaseTime();

        // seconds to ticks conversion
        return duration * 20;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(OCMEntityDamageByEntityEvent event){
        Entity damager = event.getDamager();

        double weaknessModifier = event.getWeaknessModifier();

        if(weaknessModifier != 0){
            event.setIsWeaknessModifierMultiplier(module().getBoolean("weakness.multiplier"));
            double newWeaknessModifier = module().getDouble("weakness.modifier");
            event.setWeaknessModifier(newWeaknessModifier);
            debug("Old weakness modifier: " + weaknessModifier + " New: " + newWeaknessModifier, damager);
        }

        double strengthModifier = event.getStrengthModifier();

        if(strengthModifier != 0){
            event.setIsStrengthModifierMultiplier(module().getBoolean("strength.multiplier"));
            event.setIsStrengthModifierAddend(module().getBoolean("strength.addend"));
            double newStrengthModifier = module().getDouble("strength.modifier");
            event.setStrengthModifier(newStrengthModifier);
            debug("Old strength modifier: " + strengthModifier + " New: " + newStrengthModifier, damager);
        }
    }
}
