package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.ConfigUtils;
import gvlfm78.plugin.OldCombatMechanics.utilities.Messenger;
import gvlfm78.plugin.OldCombatMechanics.utilities.MobDamage;
import gvlfm78.plugin.OldCombatMechanics.utilities.potions.GenericPotionDurations;
import gvlfm78.plugin.OldCombatMechanics.utilities.potions.PotionDurations;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
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

    public ModuleOldPotionEffects(OCMMain plugin) {
        super(plugin, "old-potion-effects");
        reload();
    }

    @Override
    public void reload(){
        durations = ConfigUtils.loadPotionDurationsList(module());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDrinksPotion(PlayerItemConsumeEvent event){
        ItemStack item = event.getItem();
        if(item.getType() != Material.POTION) return;
        event.setCancelled(true);

        PotionMeta potionMeta = (PotionMeta) item.getItemMeta();
        PotionData potionData = potionMeta.getBasePotionData();
        PotionType potionType = potionData.getType();

        int duration = getPotionDuration(potionData);

        PotionEffectType pet = potionType.getEffectType();

        int amplifier = potionData.isUpgraded() ? 1 : 0;

        PotionEffect pe = new PotionEffect(pet, duration, amplifier);

        Player player = event.getPlayer();
        setNewPotionEffect(player,pet,pe);

        //Remove item from hand since we cancelled the event
        if(player.getGameMode() != GameMode.SURVIVAL) return;

        PlayerInventory pi = player.getInventory();
        if(pi.getItemInMainHand().getType() == Material.POTION)
            pi.setItemInMainHand(new ItemStack(Material.AIR));
        else pi.setItemInOffHand(new ItemStack(Material.AIR));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionSplash(PotionSplashEvent event){
        event.setCancelled(true);
        //Change the duration using item meta and set it back
        //Use durations defined in config

        ThrownPotion thrownPotion = event.getPotion();
        ItemStack potion = thrownPotion.getItem();
        PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();

        PotionEffect potionEffect = (PotionEffect) thrownPotion.getEffects().toArray()[0];
        PotionData potionData = potionMeta.getBasePotionData();

        int duration = getPotionDuration(potionData);

        PotionEffectType pet = potionEffect.getType();

        PotionEffect pe = new PotionEffect(pet, duration, potionEffect.getAmplifier());

        event.getAffectedEntities().forEach(livingEntity -> setNewPotionEffect(livingEntity,pet,pe));
    }

    private void setNewPotionEffect(LivingEntity livingEntity, PotionEffectType pet, PotionEffect pe){
        if(livingEntity.hasPotionEffect(pet)){
            PotionEffect activepe = livingEntity.getPotionEffect(pet);

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

    private void amendPotionDuration(PotionMeta potionMeta){

    }

    private int getPotionDuration(PotionData potionData){
        PotionType potionType = potionData.getType();
        GenericPotionDurations potionDurations = durations.get(potionType).getSplash();

        int duration;
        if(potionData.isExtended()) duration = potionDurations.getExtendedTime();
        else if(potionData.isUpgraded()) duration = potionDurations.getIITime();
        else duration = potionDurations.getBaseTime();

        return duration * 20;
    }

    // Original code for strength potions by Byteflux at https://github.com/MinelinkNetwork/LegacyStrength
    // Heavily modified by gvlfm78 to change 1.9+ effects into 1.8 effects
    //@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        Messenger.debug("Old potion effects module called");

        // Do nothing if event is inherited (hacky way to ignore mcMMO AoE attacks)
        if (event.getClass() != EntityDamageByEntityEvent.class) return;

        Messenger.debug("Event is not inherited");

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        Messenger.debug("It is an entity attack");

        // Do nothing if damage is not directly from a player
        Entity entity = event.getDamager();
        if (!(entity instanceof Player)) return;

        Messenger.debug("Damager is a player");

        // Do nothing if player doesn't have the Strength effect
        Player player = (Player) event.getDamager();
        if (!player.hasPotionEffect(PotionEffectType.INCREASE_DAMAGE)) return;

        Messenger.debug("Player has strength effect");

        // event.setDamage(StrengthDamageHelper.convertDamage(player, event.getDamage()));

        // Change damage to have Strength I be 130% instead of +3 and Strength II 260% instead of +6

        // A critical hit is 150% of the weapon's damage including potion effects, but before enchantments are applied, rounded down to the nearest hit point

        //todo try-catch in case number already used
        /*PotionEffectType.INCREASE_DAMAGE.createEffect(1,1);
        PotionEffectType.registerPotionEffectType(new PotionEffectType(50){
            @Override
            public double getDurationModifier() {
                return 1;
            }

            @Override
            public String getName() {
                return "Old_Strength";
            }

            @Override
            public boolean isInstant() {
                return false;
            }

            @Override
            public Color getColor(){
                return Color.PURPLE;
            }
        });

        onAttack(event, player);*/
    }

    private void onAttack(EntityDamageByEntityEvent e, Player p) {
        ItemStack item = p.getInventory().getItemInMainHand();
        Material mat = item.getType();
        EntityType entity = e.getEntityType();

        //The raw amount of damage, including enchantments, potion effects and critical hits (?)
        double rawDamage = e.getDamage();

        //The total damage added because of enchantments
        double enchantmentDamage = (MobDamage.applyEntityBasedDamage(entity, item, rawDamage)
                + getSharpnessDamage(item.getEnchantmentLevel(Enchantment.DAMAGE_ALL))) - rawDamage;

        //Amount of damage including potion effects and critical hits
        double noEnchDamage = rawDamage - enchantmentDamage;

        PotionEffect fx = p.getPotionEffect(PotionEffectType.INCREASE_DAMAGE);
        int amplifier = fx.getAmplifier();
        //amp 0 = Strength I    amp 1 = Strength II

        /*double newDamage = (baseDamage - enchantmentDamage) / divider;
        newDamage += enchantmentDamage;//Re-add damage from enchantments
        if (newDamage < 0) newDamage = 0;
        e.setDamage(newDamage);
        debug("Item: " + mat.toString() + " Old Damage: " + baseDamage + " Enchantment Damage: " + enchantmentDamage +
                        " Divider: " + divider + " Afterwards damage: " + e.getFinalDamage() + " ======== New damage: " + newDamage
                , p);*/
    }

    private double getSharpnessDamage(int level) {
        return level >= 1 ? level * 1.25 : 0;
    }
}
