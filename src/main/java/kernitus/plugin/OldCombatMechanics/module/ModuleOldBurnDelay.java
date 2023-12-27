/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Bring back old fire burning delay behaviour
 */
public class ModuleOldBurnDelay extends OCMModule {

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
        if (e.getCause() == EntityDamageEvent.DamageCause.FIRE) {
            final Entity entity = e.getEntity();
            if(!isEnabled(entity)) return;

            entity.setFireTicks(fireTicks);
            debug("Setting fire ticks to " + fireTicks, entity);
        }
    }
}
