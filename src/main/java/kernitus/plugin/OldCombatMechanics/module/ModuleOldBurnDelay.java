package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

public class ModuleOldBurnDelay extends Module {

    /**
     * Bring back old fire burning delay behaviour
     *
     * @param plugin the plugin instance
     */
    public ModuleOldBurnDelay(OCMMain plugin) {
        super(plugin, "old-burn-delay");
    }

    @EventHandler
    public void onFireTick(EntityDamageEvent entityDamageEvent) {
        if (entityDamageEvent.getCause() == EntityDamageEvent.DamageCause.FIRE) {
            entityDamageEvent.getEntity().setFireTicks(module().getInt("fire-ticks"));
        }
    }
}
