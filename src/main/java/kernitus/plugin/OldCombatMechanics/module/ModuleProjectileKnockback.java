/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Locale;

/**
 * Adds knockback to eggs, snowballs and ender pearls.
 */
public class ModuleProjectileKnockback extends OCMModule {

    public ModuleProjectileKnockback(OCMMain plugin) {
        super(plugin, "projectile-knockback");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onEntityHit(EntityDamageByEntityEvent e) {
        if (!isEnabled(e.getDamager(), e.getEntity())) return;

        final EntityType type = e.getDamager().getType();

        switch (type) {
            case SNOWBALL: case EGG: case ENDER_PEARL:
                if (e.getDamage() == 0.0) { // So we don't override enderpearl fall damage
                    e.setDamage(module().getDouble("damage." + type.toString().toLowerCase(Locale.ROOT)));
                    if (e.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION))
                        e.setDamage(EntityDamageEvent.DamageModifier.ABSORPTION, 0);
                }
        }

    }
}