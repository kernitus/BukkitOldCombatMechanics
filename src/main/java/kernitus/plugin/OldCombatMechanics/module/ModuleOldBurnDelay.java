package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Bring back old fire burning delay behaviour
 */
public class ModuleOldBurnDelay extends Module {

    private int fireTicks;

    public ModuleOldBurnDelay(OCMMain plugin) {
        super(plugin, "old-burn-delay");
        reload();
    }

    @Override
    public void reload() {
       fireTicks = module().getInt("fire-ticks");
    }

    @EventHandler
    public void onFireTick(EntityDamageEvent e) {
        final Entity entity = e.getEntity();

        if (!isEnabled(entity.getWorld())) return;

        if (e.getCause() == EntityDamageEvent.DamageCause.FIRE){
            entity.setFireTicks(fireTicks);
            debug("Setting fire ticks to " + fireTicks, entity);
        }
    }
}
