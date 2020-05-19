package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

public class ModuleOldFireDamageDelay extends Module {

    /**
     * Bring back old fire damage delay behaviour
     *
     * @param plugin the plugin instance
     */
    public ModuleOldFireDamageDelay(OCMMain plugin) {
        super(plugin, "old-fire-damage-delay");
    }

    @EventHandler
    public void onFireTick(EntityDamageEvent entityDamageEvent) {
        if (entityDamageEvent.getCause() == EntityDamageEvent.DamageCause.FIRE) {
            entityDamageEvent.getEntity().setFireTicks(module().getInt("fire-ticks"));
        }
    }
}
