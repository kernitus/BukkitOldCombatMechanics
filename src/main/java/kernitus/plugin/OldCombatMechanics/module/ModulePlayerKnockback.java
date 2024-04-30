/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Reverts knockback formula to 1.8.
 * Also disables netherite knockback resistance.
 */
public class ModulePlayerKnockback extends OCMModule {

    private double knockbackHorizontal;
    private double knockbackVertical;
    private double knockbackVerticalLimit;
    private double knockbackExtraHorizontal;
    private double knockbackExtraVertical;
    private boolean netheriteKnockbackResistance;

    private final Map<UUID, Vector> playerKnockbackHashMap = new WeakHashMap<>();

    public ModulePlayerKnockback(OCMMain plugin) {
        super(plugin, "old-player-knockback");
        reload();
    }

    @Override
    public void reload() {
        knockbackHorizontal = module().getDouble("knockback-horizontal", 0.4);
        knockbackVertical = module().getDouble("knockback-vertical", 0.4);
        knockbackVerticalLimit = module().getDouble("knockback-vertical-limit", 0.4);
        knockbackExtraHorizontal = module().getDouble("knockback-extra-horizontal", 0.5);
        knockbackExtraVertical = module().getDouble("knockback-extra-vertical", 0.1);
        netheriteKnockbackResistance = module().getBoolean("enable-knockback-resistance", false) && Reflector.versionIsNewerOrEqualTo(1, 16, 0);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        playerKnockbackHashMap.remove(e.getPlayer().getUniqueId());
    }

    // Vanilla does its own knockback, so we need to set it again.
    // priority = lowest because we are ignoring the existing velocity, which could break other plugins
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerVelocityEvent(PlayerVelocityEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        if (!playerKnockbackHashMap.containsKey(uuid)) return;
        event.setVelocity(playerKnockbackHashMap.get(uuid));
        playerKnockbackHashMap.remove(uuid);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Disable netherite kb, the knockback resistance attribute makes the velocity event not be called
        final Entity entity = event.getEntity();
        if (!(entity instanceof Player) || netheriteKnockbackResistance) return;
        final Player damagee = (Player) entity;

        // This depends on the attacker's combat mode
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && event instanceof EntityDamageByEntityEvent) {
            final Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
            if(!isEnabled(damager)) return;
        } else {
            if(!isEnabled(damagee)) return;
        }

        final AttributeInstance attribute = damagee.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        attribute.getModifiers().forEach(attribute::removeModifier);
    }

    // Monitor priority because we don't modify anything here, but apply on velocity change event
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageEntity(EntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        if (!(damager instanceof LivingEntity)) return;
        final LivingEntity attacker = (LivingEntity) damager;

        final Entity damagee = event.getEntity();
        if (!(damagee instanceof Player)) return;
        final Player victim = (Player) damagee;

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) > 0) return;

        if(attacker instanceof HumanEntity){
            if (!isEnabled(attacker)) return;
        } else if(!isEnabled(victim)) return;

        // Figure out base knockback direction
        Location attackerLocation = attacker.getLocation();
        Location victimLocation = victim.getLocation();
        double d0 = attackerLocation.getX() - victimLocation.getX();
        double d1;

        for (d1 = attackerLocation.getZ() - victimLocation.getZ();
             d0 * d0 + d1 * d1 < 1.0E-4D; d1 = (Math.random() - Math.random()) * 0.01D) {
            d0 = (Math.random() - Math.random()) * 0.01D;
        }

        final double magnitude = Math.sqrt(d0 * d0 + d1 * d1);

        // Get player knockback before any friction is applied
        final Vector playerVelocity = victim.getVelocity();

        // Apply friction, then add base knockback
        playerVelocity.setX((playerVelocity.getX() / 2) - (d0 / magnitude * knockbackHorizontal));
        playerVelocity.setY((playerVelocity.getY() / 2) + knockbackVertical);
        playerVelocity.setZ((playerVelocity.getZ() / 2) - (d1 / magnitude * knockbackHorizontal));

        // Calculate bonus knockback for sprinting or knockback enchantment levels
        final EntityEquipment equipment = attacker.getEquipment();
        if (equipment != null) {
            final ItemStack heldItem = equipment.getItemInMainHand().getType() == Material.AIR ?
                    equipment.getItemInOffHand() : equipment.getItemInMainHand();

            int bonusKnockback = heldItem.getEnchantmentLevel(Enchantment.KNOCKBACK);
            if (attacker instanceof Player && ((Player) attacker).isSprinting()) ++bonusKnockback;

            if (playerVelocity.getY() > knockbackVerticalLimit) playerVelocity.setY(knockbackVerticalLimit);

            if (bonusKnockback > 0) { // Apply bonus knockback
                playerVelocity.add(new Vector((-Math.sin(attacker.getLocation().getYaw() * 3.1415927F / 180.0F) *
                        (float) bonusKnockback * knockbackExtraHorizontal), knockbackExtraVertical,
                        Math.cos(attacker.getLocation().getYaw() * 3.1415927F / 180.0F) *
                                (float) bonusKnockback * knockbackExtraHorizontal));
            }
        }

        if (netheriteKnockbackResistance) {
            // Allow netherite to affect the horizontal knockback. Each piece of armour yields 10% resistance
            final double resistance = 1 - victim.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).getValue();
            playerVelocity.multiply(new Vector(resistance, 1, resistance));
        }

        final UUID victimId = victim.getUniqueId();

        // Knockback is sent immediately in 1.8+, there is no reason to send packets manually
        playerKnockbackHashMap.put(victimId, playerVelocity);

        // Sometimes PlayerVelocityEvent doesn't fire, remove data to not affect later events if that happens
        Bukkit.getScheduler().runTaskLater(plugin, () -> playerKnockbackHashMap.remove(victimId), 1);
    }
}