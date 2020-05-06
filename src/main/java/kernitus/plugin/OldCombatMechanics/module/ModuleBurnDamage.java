package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

public class ModuleBurnDamage extends Module {

    /**
     * Bring back old fire behavior
     *
     * @param plugin the plugin instance
     */
    public ModuleBurnDamage(OCMMain plugin) {
        super(plugin, "fire-damage");
    }

    @EventHandler
    public void onFireTick(EntityDamageEvent entityDamageEvent) {
        if (entityDamageEvent.getCause() == EntityDamageEvent.DamageCause.FIRE) {
            entityDamageEvent.getEntity().setFireTicks(this.module().getInt("fire-ticks"));
        }
    }
}
