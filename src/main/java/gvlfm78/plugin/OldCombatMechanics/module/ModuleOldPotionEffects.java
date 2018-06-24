package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.MobDamage;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

// Original code for strength potions by Byteflux at https://github.com/MinelinkNetwork/LegacyStrength
// Heavily modified by gvlfm78 to change 1.9+ effects into 1.8 effects

public class ModuleOldPotionEffects extends Module {

    public ModuleOldPotionEffects(OCMMain plugin) {
        super(plugin, "old-potion-effects");
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void recalculateDamage(EntityDamageByEntityEvent event) {

        // Do nothing if event is inherited (hacky way to ignore mcMMO AoE attacks)
        if (event.getClass() != EntityDamageByEntityEvent.class) return;

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;

        // Do nothing if damage is not directly from a player
        Entity entity = event.getDamager();
        if (!(entity instanceof Player)) return;

        // Do nothing if player doesn't have the Strength effect
        Player player = (Player) event.getDamager();
        if (!player.hasPotionEffect(PotionEffectType.INCREASE_DAMAGE)) return;

        // event.setDamage(StrengthDamageHelper.convertDamage(player, event.getDamage()));

        // Change damage to have Strength I be 130% instead of +3 and Strength II 260% instead of +6


        // A critical hit is 150% of the weapon's damage including potion effects, but before enchantments are applied, rounded down to the nearest hit point

        onAttack(event, player);
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
