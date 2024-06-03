/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffectTypeCompat;
import kernitus.plugin.OldCombatMechanics.utilities.potions.PotionEffects;
import kernitus.plugin.OldCombatMechanics.versions.enchantments.EnchantmentCompat;
import org.bukkit.Material;
import org.bukkit.entity.*;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Optional;

import static kernitus.plugin.OldCombatMechanics.utilities.Messenger.debug;

public class OCMEntityDamageByEntityEvent extends Event implements Cancellable {

    private boolean cancelled;
    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    private final Entity damager, damagee;
    private final DamageCause cause;
    private double rawDamage;

    private ItemStack weapon;
    private int sharpnessLevel;
    private boolean hasWeakness;
    // The levels as shown in-game, i.e. 1 or 2 corresponding to I and II
    private int strengthLevel, weaknessLevel;

    private double baseDamage = 0, mobEnchantmentsDamage = 0, sharpnessDamage = 0, criticalMultiplier = 1;
    private double strengthModifier = 0, weaknessModifier = 0;

    // In 1.9 strength modifier is an addend, in 1.8 it is a multiplier and addend (+130%)
    private boolean isStrengthModifierMultiplier = false;
    private boolean isStrengthModifierAddend = true;
    private boolean isWeaknessModifierMultiplier = false;

    private boolean was1_8Crit = false;
    private boolean wasSprinting = false;

    // Here we reverse-engineer all the various damages caused by removing them one at a time, backwards from what NMS code does.
    // This is so the modules can listen to this event and make their modifications, then EntityDamageByEntityListener sets the new values back.
    // Performs the opposite of the following:
    // (Base + Potion effects, scaled by attack delay) + Critical Hit + (Enchantments, scaled by attack delay), Overdamage, Armour
    public OCMEntityDamageByEntityEvent(Entity damager, Entity damagee, DamageCause cause, double rawDamage) {
        this.damager = damager;
        this.damagee = damagee;
        this.cause = cause;

        // We ignore attacks like arrows etc. because we do not need to change the attack side of those
        // Other modules such as old armour strength work independently of this event
        if (!(damager instanceof LivingEntity)) {
            setCancelled(true);
            return;
        }

        // The raw damage passed to this event is EDBE's BASE damage, which does not include armour effects or resistance etc (defence)
        this.rawDamage = rawDamage;

        /*
        Invulnerability will cause less damage if they attack with a stronger weapon while vulnerable.
        We must detect this and account for it, instead of setting the usual base weapon damage.
        We artificially set the last damage to 0 between events so that all hits will register,
        however we only do this for DamageByEntity, so there could still be environmental damage (e.g. cactus).
        */
        if (damagee instanceof LivingEntity) {
            final LivingEntity livingDamagee = (LivingEntity) damagee;
            if ((float) livingDamagee.getNoDamageTicks() > (float) livingDamagee.getMaximumNoDamageTicks() / 2.0F) {
                // NMS code also checks if current damage is higher that previous damage. However, here the event
                // already has the difference between the two as the raw damage, and the event does not fire at all
                // if this precondition is not met.

                // Adjust for last damage being environmental sources (e.g. cactus, fall damage)
                final double lastDamage = livingDamagee.getLastDamage();
                this.rawDamage = rawDamage + lastDamage;

                debug(livingDamagee, "Overdamaged!: " + livingDamagee.getNoDamageTicks() + "/" +
                        livingDamagee.getMaximumNoDamageTicks() + " last: " + livingDamagee.getLastDamage());
            } else {
                debug(livingDamagee, "Invulnerability: " + livingDamagee.getNoDamageTicks() + "/" +
                        livingDamagee.getMaximumNoDamageTicks() + " last: " + livingDamagee.getLastDamage());
            }
        }

        final LivingEntity livingDamager = (LivingEntity) damager;

        weapon = livingDamager.getEquipment().getItemInMainHand();
        // Yay paper. Why do you need to return null here?
        if (weapon == null) weapon = new ItemStack(Material.AIR);
        // Technically the weapon could be in the offhand, i.e. a bow.
        // However, we are only concerned with melee weapons here, which will always be in the main hand.

        final EntityType damageeType = damagee.getType();

        debug(livingDamager, "Raw attack damage: " + rawDamage);
        debug(livingDamager, "Without overdamage: " + this.rawDamage);


        mobEnchantmentsDamage = MobDamage.getEntityEnchantmentsDamage(damageeType, weapon);
        sharpnessLevel = weapon.getEnchantmentLevel(EnchantmentCompat.SHARPNESS.get());
        sharpnessDamage = DamageUtils.getNewSharpnessDamage(sharpnessLevel);

        // Scale enchantment damage by attack cooldown
        if (damager instanceof HumanEntity) {
            final float cooldown = DamageUtils.getAttackCooldown.apply((HumanEntity) damager, 0.5F);
            mobEnchantmentsDamage *= cooldown;
            sharpnessDamage *= cooldown;
        }

        debug(livingDamager, "Mob: " + mobEnchantmentsDamage + " Sharpness: " + sharpnessDamage);

        // Amount of damage including potion effects and critical hits
        double tempDamage = this.rawDamage - mobEnchantmentsDamage - sharpnessDamage;

        debug(livingDamager, "No ench damage: " + tempDamage);

        // Check if it's a critical hit
        if (livingDamager instanceof Player && DamageUtils.isCriticalHit1_8((HumanEntity) livingDamager)){
            was1_8Crit = true;
            debug(livingDamager, "1.8 Critical hit detected");
            // In 1.9 a crit also requires the player not to be sprinting
            if (DamageUtils.isCriticalHit1_9((Player) livingDamager)) {
                debug(livingDamager, "1.9 Critical hit detected");
                debug("1.9 Critical hit detected");
                criticalMultiplier = 1.5;
                tempDamage /= 1.5;
            }
        }

        // Un-scale the damage by the attack strength
        if (damager instanceof HumanEntity) {
            final float cooldown = DamageUtils.getAttackCooldown.apply((HumanEntity) damager, 0.5F);
            tempDamage /= 0.2F + cooldown * cooldown * 0.8F;
        }

        // amplifier 0 = Strength I    amplifier 1 = Strength II
        strengthLevel = PotionEffects.get(livingDamager, PotionEffectTypeCompat.STRENGTH.get())
                .map(PotionEffect::getAmplifier)
                .orElse(-1) + 1;

        strengthModifier = strengthLevel * 3;

        debug(livingDamager, "Strength Modifier: " + strengthModifier);

        // Don't set has weakness if amplifier is > 0 or < -1, which is outside normal range and probably set by plugin
        // We use an amplifier of -1 (Level 0) to have no effect so weaker attacks will register
        final Optional<Integer> weaknessAmplifier = PotionEffects.get(livingDamager, PotionEffectType.WEAKNESS).map(PotionEffect::getAmplifier);
        hasWeakness = weaknessAmplifier.isPresent() && (weaknessAmplifier.get() == -1 || weaknessAmplifier.get() == 0);
        weaknessLevel = weaknessAmplifier.orElse(-1) + 1;

        weaknessModifier = weaknessLevel * -4;

        debug(livingDamager, "Weakness Modifier: " + weaknessModifier);

        baseDamage = tempDamage + weaknessModifier - strengthModifier;
        debug(livingDamager, "Base tool damage: " + baseDamage);
    }

    public Entity getDamager() {
        return damager;
    }

    public Entity getDamagee() {
        return damagee;
    }

    public DamageCause getCause() {
        return cause;
    }

    public double getRawDamage() {
        return rawDamage;
    }

    public ItemStack getWeapon() {
        return weapon;
    }

    public int getSharpnessLevel() {
        return sharpnessLevel;
    }

    public double getStrengthModifier() {
        return strengthModifier;
    }

    public void setStrengthModifier(double strengthModifier) {
        this.strengthModifier = strengthModifier;
    }

    public int getStrengthLevel() {
        return strengthLevel;
    }

    /**
     * Whether the attacker had the weakness potion effect,
     * and the level of the effect was either 0 (used by OCM) or 1 (normal value).
     * Values outside this range are to be ignored, as they are probably from other plugins.
     */
    public boolean hasWeakness() {
        return hasWeakness;
    }

    public int getWeaknessLevel() {
        return weaknessLevel;
    }

    public double getWeaknessModifier() {
        return weaknessModifier;
    }

    public void setWeaknessModifier(double weaknessModifier) {
        this.weaknessModifier = weaknessModifier;
    }

    public void setWeaknessLevel(int weaknessLevel) {
        this.weaknessLevel = weaknessLevel;
    }

    public boolean isStrengthModifierMultiplier() {
        return isStrengthModifierMultiplier;
    }

    public void setIsStrengthModifierMultiplier(boolean isStrengthModifierMultiplier) {
        this.isStrengthModifierMultiplier = isStrengthModifierMultiplier;
    }

    public void setIsStrengthModifierAddend(boolean isStrengthModifierAddend) {
        this.isStrengthModifierAddend = isStrengthModifierAddend;
    }

    public boolean isWeaknessModifierMultiplier() {
        return isWeaknessModifierMultiplier;
    }

    public void setIsWeaknessModifierMultiplier(boolean weaknessModifierMultiplier) {
        isWeaknessModifierMultiplier = weaknessModifierMultiplier;
    }

    public boolean isStrengthModifierAddend() {
        return isStrengthModifierAddend;
    }

    public double getBaseDamage() {
        return baseDamage;
    }

    public void setBaseDamage(double baseDamage) {
        this.baseDamage = baseDamage;
    }

    public double getMobEnchantmentsDamage() {
        return mobEnchantmentsDamage;
    }

    public void setMobEnchantmentsDamage(double mobEnchantmentsDamage) {
        this.mobEnchantmentsDamage = mobEnchantmentsDamage;
    }

    public double getSharpnessDamage() {
        return sharpnessDamage;
    }

    public void setSharpnessDamage(double sharpnessDamage) {
        this.sharpnessDamage = sharpnessDamage;
    }

    public double getCriticalMultiplier() {
        return criticalMultiplier;
    }

    public void setCriticalMultiplier(double criticalMultiplier) {
        this.criticalMultiplier = criticalMultiplier;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    public boolean wasSprinting() {
        return wasSprinting;
    }

    public void setWasSprinting(boolean wasSprinting) {
        this.wasSprinting = wasSprinting;
    }

    public boolean was1_8Crit() {
        return was1_8Crit;
    }

    public void setWas1_8Crit(boolean was1_8Crit) {
        this.was1_8Crit = was1_8Crit;
    }
}
