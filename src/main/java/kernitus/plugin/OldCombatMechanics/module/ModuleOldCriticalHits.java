/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import org.bukkit.event.EventHandler;

import java.util.Random;

public class ModuleOldCriticalHits extends Module {

    public ModuleOldCriticalHits(OCMMain plugin) {
        super(plugin, "old-critical-hits");
        reload();
    }

    private boolean isMultiplierRandom, allowSprinting, roundDown;
    private double multiplier, addend;
    private Random random;

    @Override
    public void reload() {
        random = new Random();

        isMultiplierRandom = module().getBoolean("is-multiplier-random", true);
        allowSprinting = module().getBoolean("allowSprinting", true);
        roundDown = module().getBoolean("roundDown", true);
        multiplier = module().getDouble("multiplier", 1.5);
        addend = module().getDouble("addend", 1);
    }

    @EventHandler
    public void onOCMDamage(OCMEntityDamageByEntityEvent e) {
        if (!isEnabled(e.getDamagee().getWorld())) return;

        // In 1.9 a critical hit requires the player not to be sprinting
        if (e.was1_8Crit() && (allowSprinting || !e.wasSprinting())) {
            // Recalculate according to 1.8 rules: https://minecraft.fandom.com/wiki/Damage?oldid=706258#Critical_hits
            // That is, the attack deals a random amount of additional damage, up to 50% more (rounded down) plus one heart.
            // Bukkit 1.8_r3 code:    i += this.random.nextInt(i / 2 + 2);
            // We instead generate a random multiplier between 1 and 1.5 (or user configured)
            double actualMultiplier = isMultiplierRandom ? (1 + random.nextDouble() * (multiplier - 1)) : multiplier;
            e.setCriticalMultiplier(actualMultiplier);
            e.setCriticalAddend(addend);
            e.setRoundCritDamage(roundDown);
        }
    }
}
