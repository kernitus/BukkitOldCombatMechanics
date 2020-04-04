package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.Locale;
import java.util.Random;

/**
 * Adds knockback to eggs, snowballs and ender pearls.
 */
public class ModuleProjectileKnockback extends Module {

    public ModuleProjectileKnockback(OCMMain plugin) {
        super(plugin, "projectile-knockback");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityHit(EntityDamageByEntityEvent e) {
        if (!isEnabled(e.getEntity().getWorld())) {
            return;
        }

        EntityType type = e.getDamager().getType();
        final Entity attacker = e.getDamager();
        final Entity victim = e.getEntity();

        switch (type) {
            case SNOWBALL:
            case EGG:
            case ENDER_PEARL:
                if (victim instanceof LivingEntity) {
                    LivingEntity livingVictim = ((LivingEntity) victim);
                    if (livingVictim.getAbsorptionAmount() > 0) {
                        livingVictim.damage(module().getDouble("damage." + type.toString().toLowerCase(Locale.ROOT)), attacker);

                        double randomValue = 0.35 + (0.1) * new Random().nextDouble();

                        // mocking Minecraft knockback calculation
                        final Vector knockBack = victim.getLocation().toVector().subtract(attacker.getLocation().toVector()).normalize().multiply(randomValue).setY(0.4);
                        victim.setVelocity(knockBack);
                        e.setCancelled(true);
                        break;
                    }
                }
                e.setDamage(module().getDouble("damage." + type.toString().toLowerCase(Locale.ROOT)));
            default:
                break;
        }

    }
}