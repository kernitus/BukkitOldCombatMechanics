package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
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

import java.util.HashMap;
import java.util.UUID;

/**
 * Reverts knockback formula to 1.8.
 * Also disables netherite knockback resistance.
 */
public class ModulePlayerKnockback extends Module {

    private double knockbackHorizontal;
    private double knockbackVertical;
    private double knockbackVerticalLimit;
    private double knockbackExtraHorizontal;
    private double knockbackExtraVertical;
    private boolean netheriteKnockbackResistance;

    private final HashMap<UUID, Vector> playerKnockbackHashMap = new HashMap<>();

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

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        playerKnockbackHashMap.remove(e.getPlayer().getUniqueId());
    }

    // Vanilla does its own knockback, so we need to set it again.
    // priority = lowest because we are ignoring the existing velocity, which could break other plugins
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerVelocityEvent(PlayerVelocityEvent event) {
        final UUID uuid = event.getPlayer().getUniqueId();
        if (!playerKnockbackHashMap.containsKey(uuid)) return;
        event.setVelocity(playerKnockbackHashMap.get(uuid));
        playerKnockbackHashMap.remove(uuid);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        // Disable netherite kb, the knockback resistance attribute makes the velocity event not be called
        if (!(event.getEntity() instanceof Player) || netheriteKnockbackResistance) return;
        final AttributeInstance attribute = ((Player) event.getEntity()).getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        for (AttributeModifier modifier : attribute.getModifiers())
            attribute.removeModifier(modifier);
    }

    // Monitor priority because we don't modify anything here, but apply on velocity change event
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDamageEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof LivingEntity)) return;
        final LivingEntity attacker = (LivingEntity) event.getDamager();

        if (!isEnabled(attacker.getWorld())) return;

        if (!(event.getEntity() instanceof Player)) return;

        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (event.getDamage(EntityDamageEvent.DamageModifier.BLOCKING) > 0) return;

        final Player victim = (Player) event.getEntity();

        // Figure out base knockback direction
        double d0 = attacker.getLocation().getX() - victim.getLocation().getX();
        double d1;

        for (d1 = attacker.getLocation().getZ() - victim.getLocation().getZ();
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

        // Knockback is sent immediately in 1.8+, there is no reason to send packets manually
        playerKnockbackHashMap.put(victim.getUniqueId(), playerVelocity);
    }
}