/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils;
import kernitus.plugin.OldCombatMechanics.utilities.damage.NewWeaponDamage;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Locale;

/**
 * Restores old tool damage.
 */
public class ModuleOldToolDamage extends OCMModule {

    private static final String[] WEAPONS = {"sword", "axe", "pickaxe", "spade", "shovel", "hoe"};
    private boolean oldSharpness;

    public ModuleOldToolDamage(OCMMain plugin) {
        super(plugin, "old-tool-damage");
        reload();
    }

    @Override
    public void reload() {
        oldSharpness = module().getBoolean("old-sharpness", true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamaged(OCMEntityDamageByEntityEvent event) {
        final Entity damager = event.getDamager();
        if (event.getCause() == EntityDamageEvent.DamageCause.THORNS) return;

        if (!isEnabled(damager, event.getDamagee())) return;

        final ItemStack weapon = event.getWeapon();
        final Material weaponMaterial = weapon.getType();
        debug("Weapon material: " + weaponMaterial);

        if (!isWeapon(weaponMaterial)) return;

        // If damage was not what we expected, ignore it because it's probably a custom weapon or from another plugin
        final double oldBaseDamage = event.getBaseDamage();
        final double expectedBaseDamage = NewWeaponDamage.getDamage(weaponMaterial);
        // We check difference as calculation inaccuracies can make it not match
        if (Math.abs(oldBaseDamage - expectedBaseDamage) > 0.0001) {
            debug("Expected " + expectedBaseDamage + " got " + oldBaseDamage + " ignoring weapon...");
            return;
        }

        final double newWeaponBaseDamage = WeaponDamages.getDamage(weaponMaterial);
        if (newWeaponBaseDamage <= 0) {
            debug("Unknown tool type: " + weaponMaterial, damager);
            return;
        }

        event.setBaseDamage(newWeaponBaseDamage);
        Messenger.debug("Old tool damage: " + oldBaseDamage + " New: " + newWeaponBaseDamage);


        // Set sharpness to 1.8 damage value
        final int sharpnessLevel = event.getSharpnessLevel();
        double newSharpnessDamage = oldSharpness ?
                DamageUtils.getOldSharpnessDamage(sharpnessLevel) :
                DamageUtils.getNewSharpnessDamage(sharpnessLevel);

        debug("Old sharpness damage: " + event.getSharpnessDamage() + " New: " + newSharpnessDamage, damager);
        event.setSharpnessDamage(newSharpnessDamage);

        // The mob enchantments damage remains the same and is linear, no need to recalculate it
    }

    private boolean isWeapon(Material material) {
        return Arrays.stream(WEAPONS).anyMatch(type -> isOfType(material, type));
    }

    private boolean isOfType(Material mat, String type) {
        return mat.toString().endsWith("_" + type.toUpperCase(Locale.ROOT));
    }
}
