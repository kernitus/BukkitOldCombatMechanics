/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import org.bukkit.event.EventHandler;

public class ModuleOldCriticalHits extends OCMModule {

    private boolean allowSprinting;
    private double multiplier;

    public ModuleOldCriticalHits(OCMMain plugin) {
        super(plugin, "old-critical-hits");
        reload();
    }

    @Override
    public void reload() {
        allowSprinting = module().getBoolean("allowSprinting", true);
        multiplier = module().getDouble("multiplier", 1.5);
    }

    @EventHandler
    public void onOCMDamage(OCMEntityDamageByEntityEvent e) {
        if (!isEnabled(e.getDamager(), e.getDamagee())) return;

        // In 1.9, a critical hit requires the player not to be sprinting
        if (e.was1_8Crit() && (allowSprinting || !e.wasSprinting()))
            e.setCriticalMultiplier(multiplier);
    }
}
