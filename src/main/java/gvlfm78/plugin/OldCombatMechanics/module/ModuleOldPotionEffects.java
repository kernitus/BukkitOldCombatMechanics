package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.ConfigUtils;
import gvlfm78.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import gvlfm78.plugin.OldCombatMechanics.utilities.potions.GenericPotionDurations;
import gvlfm78.plugin.OldCombatMechanics.utilities.potions.PotionDurations;
import gvlfm78.plugin.OldCombatMechanics.utilities.potions.PotionEffects;
import org.apache.commons.lang.ArrayUtils;
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

import java.util.HashMap;

public class ModuleOldPotionEffects extends Module {

    private HashMap<PotionType, PotionDurations> durations;
    private PotionType[] excludedPotionTypes =
            {PotionType.INSTANT_DAMAGE, PotionType.INSTANT_HEAL, PotionType.LUCK,
                    PotionType.AWKWARD, PotionType.MUNDANE, PotionType.THICK,
                    PotionType.UNCRAFTABLE, PotionType.WATER};

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
        ItemStack item = event.getItem();
        if(item.getType() != Material.POTION) return;

        PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
        PotionData potionData = potionMeta.getBasePotionData();
        PotionType potionType = potionData.getType();

        if(isExcludedPotion(potionType)) return;
        event.setCancelled(true);

        int duration = getPotionDuration(potionData, false);

        PotionEffectType pet = potionType.getEffectType();

        int amplifier = potionData.isUpgraded() ? 1 : 0;

        PotionEffect pe = new PotionEffect(pet, duration, amplifier);

        Player player = event.getPlayer();
        setNewPotionEffect(player, pet, pe);

        //Remove item from hand since we cancelled the event
        if(player.getGameMode() != GameMode.SURVIVAL) return;

        PlayerInventory pi = player.getInventory();
        ItemStack potionItem = pi.getItemInMainHand();
        boolean isInMainHand = false;
        if(item.equals(potionItem)) isInMainHand = true;

        ItemStack glassBottle = new ItemStack(Material.GLASS_BOTTLE);

        int amount = item.getAmount() - 1;
        //If it was just one potion set item to glass bottle
        if(amount < 1)
            item = glassBottle;
        else {
            item.setAmount(amount);
            player.getInventory().addItem(glassBottle);
        }

        if(isInMainHand) pi.setItemInMainHand(item);
        else pi.setItemInOffHand(item);
    }

    /**
     * Change the duration using values defined in config
     * for splash potions
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionSplash(PotionSplashEvent event){
        ThrownPotion thrownPotion = event.getPotion();
        ItemStack potion = thrownPotion.getItem();
        PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();

        PotionEffect potionEffect = (PotionEffect) thrownPotion.getEffects().toArray()[0];
        PotionData potionData = potionMeta.getBasePotionData();

        if(isExcludedPotion(potionData.getType())) return;
        event.setCancelled(true);

        int duration = getPotionDuration(potionData, true);

        PotionEffectType pet = potionEffect.getType();

        PotionEffect pe = new PotionEffect(pet, duration, potionEffect.getAmplifier());

        event.getAffectedEntities().forEach(livingEntity -> setNewPotionEffect(livingEntity, pet, pe));
    }

    private boolean isExcludedPotion(PotionType pt){
        return ArrayUtils.contains(excludedPotionTypes, pt);
    }

    private void setNewPotionEffect(LivingEntity livingEntity, PotionEffectType pet, PotionEffect pe){
        if(livingEntity.hasPotionEffect(pet)){
            PotionEffect activepe = PotionEffects.getOrNull(livingEntity, pet);

            int remainingDuration = activepe.getDuration();

            //If new effect it type II while old wasn't, or
            // new would last longer than remaining time but isn't a level downgrade (eg II -> I), set it
            int newAmplifier = pe.getAmplifier();
            int activeAmplifier = activepe.getAmplifier();
            if(newAmplifier > activeAmplifier ||
                    (newAmplifier == activeAmplifier && remainingDuration < pe.getDuration()))
                livingEntity.addPotionEffect(pe, true);
        } else livingEntity.addPotionEffect(pe, false);
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
            double newStrengthModifier = module().getDouble("strength.modifier");
            event.setStrengthModifier(newStrengthModifier);
            debug("Old strength modifier: " + strengthModifier + " New: " + newStrengthModifier, damager);
        }
    }
}
