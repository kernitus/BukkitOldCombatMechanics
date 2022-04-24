/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * Restores old tool damage.
 */
public class ModuleOldToolDamage extends Module {

    private static final String[] WEAPONS = {"sword", "axe", "pickaxe", "spade", "shovel", "hoe"};

    public ModuleOldToolDamage(OCMMain plugin) {
        super(plugin, "old-tool-damage");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamaged(OCMEntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        if (event.getCause() == EntityDamageEvent.DamageCause.THORNS) return;

        final Entity damagee = event.getDamagee();
        final World world = damager.getWorld();

        if (!isEnabled(world)) return;

        final Material weaponMaterial = event.getWeapon().getType();

        if (!isTool(weaponMaterial)) return;

        double weaponDamage = WeaponDamages.getDamage(weaponMaterial);
        if (weaponDamage <= 0) {
            debug("Unknown tool type: " + weaponMaterial, damager);
            return;
        }

        final double oldBaseDamage = event.getBaseDamage();

        // If the raw is not what we expect for 1.9 we should ignore it, for compatibility with other plugins
        //if (oldBaseDamage == expectedDamage) event.setBaseDamage(weaponDamage);
        event.setBaseDamage(weaponDamage);

        debug("Old " + weaponMaterial + " damage: " + oldBaseDamage + " New tool damage: " + weaponDamage +
                (event.wasInvulnerabilityOverdamage() ? " (overdamage)" : ""), damager);

        // Set sharpness to 1.8 damage value
        final double newSharpnessDamage = DamageUtils.getOldSharpnessDamage(event.getSharpnessLevel());
        debug("Old sharpness damage: " + event.getSharpnessDamage() + " New: " + newSharpnessDamage, damager);
        event.setSharpnessDamage(newSharpnessDamage);
    }

    private boolean isTool(Material material) {
        return Arrays.stream(WEAPONS).anyMatch(type -> isOfType(material, type));
    }

    private boolean isOfType(Material mat, String type) {
        return mat.toString().endsWith("_" + type.toUpperCase(Locale.ROOT));
    }
}
