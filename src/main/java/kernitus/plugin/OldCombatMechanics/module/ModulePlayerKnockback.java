package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;

public class ModulePlayerKnockback extends Module {
    /**
     * The knockback formula is changed in 1.9, especially with sprinting
     * Netherite knockback resistance in 1.16 also changes knockback
     * <p>
     * This module is able to revert both back to 1.8 behavior
     */

    double knockbackHorizontal;
    double knockbackVertical;
    double knockbackVerticalLimit;
    double knockbackExtraHorizontal;
    double knockbackExtraVertical;
    boolean netheriteKnockbackResistance;

    HashMap<Player, Vector> playerKnockbackHashMap = new HashMap<>();

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
        netheriteKnockbackResistance = module().getBoolean("enable-knockback-resistance", false) && Reflector.versionIsNewerOrEqualAs(1, 16, 0);
    }

    // vanilla does it's own knockback, so we need to set it again.
    // priority = lowest because we are ignoring the existing velocity, which could break other plugins
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerVelocityEvent(PlayerVelocityEvent event) {
        if (!playerKnockbackHashMap.containsKey(event.getPlayer())) return;
        event.setVelocity(playerKnockbackHashMap.get(event.getPlayer()));
        playerKnockbackHashMap.remove(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageEntity(EntityDamageByEntityEvent event) {
        if (!isEnabled(event.getDamager().getWorld())) return;
        if (!(event.getDamager() instanceof Player)) return;
        if (!(event.getEntity() instanceof Player)) return;
        if (!event.getCause().equals(EntityDamageEvent.DamageCause.ENTITY_ATTACK)) return;
        if (event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) != 0) return;

        Player attacker = (Player) event.getDamager();
        Player victim = (Player) event.getEntity();

        // Figure out base knockback direction
        double d0 = attacker.getLocation().getX() - victim.getLocation().getX();
        double d1;

        for (d1 = attacker.getLocation().getZ() - victim.getLocation().getZ();
             d0 * d0 + d1 * d1 < 1.0E-4D; d1 = (Math.random() - Math.random()) * 0.01D) {
            d0 = (Math.random() - Math.random()) * 0.01D;
        }

        double magnitude = Math.sqrt(d0 * d0 + d1 * d1);

        // Get player knockback taken before any friction applied
        Vector playerVelocity = victim.getVelocity();

        // apply friction then add the base knockback
        playerVelocity.setX((playerVelocity.getX() / 2) - (d0 / magnitude * knockbackHorizontal));
        playerVelocity.setY((playerVelocity.getY() / 2) + knockbackVertical);
        playerVelocity.setZ((playerVelocity.getZ() / 2) - (d1 / magnitude * knockbackHorizontal));

        // Calculate bonus knockback for sprinting or knockback enchantment levels
        int i = attacker.getItemInHand().getEnchantmentLevel(Enchantment.KNOCKBACK);
        if (attacker.isSprinting()) ++i;

        if (playerVelocity.getY() > knockbackVerticalLimit) {
            playerVelocity.setY(knockbackVerticalLimit);
        }

        // Apply bonus knockback
        if (i > 0) {
            playerVelocity.add(new Vector((-Math.sin(attacker.getLocation().getYaw() * 3.1415927F / 180.0F) *
                    (float) i * knockbackExtraHorizontal), knockbackExtraVertical,
                    Math.cos(attacker.getLocation().getYaw() * 3.1415927F / 180.0F) *
                            (float) i * knockbackExtraHorizontal));
        }

        // Disable netherite kb, the knockback resistance attribute makes the velocity event not be called
        if (!netheriteKnockbackResistance)
            for (AttributeModifier modifier : victim.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).getModifiers()) {
                victim.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).removeModifier(modifier);
            }

        // Allow netherite to affect the horizontal knockback
        if (netheriteKnockbackResistance) {
            double resistance = 1 - victim.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE).getValue();
            playerVelocity.multiply(new Vector(resistance, 1, resistance));
        }

        // Knockback is sent immediately in 1.8+, there is no reason to send packets manually
        playerKnockbackHashMap.put(victim, playerVelocity);
    }
}