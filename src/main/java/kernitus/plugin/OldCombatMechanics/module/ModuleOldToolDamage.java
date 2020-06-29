package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;

import java.util.Locale;

/**
 * Restores old tool damage.
 */
public class ModuleOldToolDamage extends Module {

    private static final String[] WEAPONS = {"sword", "axe", "pickaxe", "spade", "shovel", "hoe"};

    public ModuleOldToolDamage(OCMMain plugin){
        super(plugin, "old-tool-damage");
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamaged(OCMEntityDamageByEntityEvent event){
        Entity damager = event.getDamager();

        World world = damager.getWorld();

        if(!isEnabled(world)) return;

        Material weaponMaterial = event.getWeapon().getType();

        if(!isTool(weaponMaterial)) return;

        double weaponDamage = WeaponDamages.getDamage(weaponMaterial);
        if(weaponDamage <= 0){
            debug("Unknown tool type: " + weaponMaterial, damager);
            return;
        }

        double oldBaseDamage = event.getBaseDamage();

        event.setBaseDamage(weaponDamage);
        debug("Old " + weaponMaterial + " damage: " + oldBaseDamage + " New tool damage: " + weaponDamage, damager);

        // Set sharpness to 1.8 damage value
        double newSharpnessDamage = DamageUtils.getOldSharpnessDamage(event.getSharpnessLevel());
        debug("Old sharpness damage: " + event.getSharpnessDamage() + " New: " + newSharpnessDamage, damager);
        event.setSharpnessDamage(newSharpnessDamage);
    }

    private boolean isTool(Material material){
        for(String type : WEAPONS)
            if(isOfType(material, type))
                return true;

        return false;
    }

    private boolean isOfType(Material mat, String type){
        return mat.toString().endsWith("_" + type.toUpperCase(Locale.ROOT));
    }
}
