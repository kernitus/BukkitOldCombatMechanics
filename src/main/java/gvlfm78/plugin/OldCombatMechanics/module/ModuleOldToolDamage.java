package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.damage.DamageUtils;
import kernitus.plugin.OldCombatMechanics.utilities.damage.OCMEntityDamageByEntityEvent;
import kernitus.plugin.OldCombatMechanics.utilities.damage.WeaponDamages;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;

public class ModuleOldToolDamage extends Module {

    private String[] weapons = {"sword", "axe", "pickaxe", "spade", "hoe"};

    public ModuleOldToolDamage(OCMMain plugin){
        super(plugin, "old-tool-damage");
    }

    @EventHandler (ignoreCancelled = true)
    public void onEntityDamaged(OCMEntityDamageByEntityEvent event){
        Entity damager = event.getDamager();

        World world = damager.getWorld();

        if(!isEnabled(world)) return;

        Material mat = event.getWeapon().getType();

        if(!isHolding(mat, weapons)) return;

        double weaponDamage = WeaponDamages.getDamage(mat);
        if(weaponDamage <= 0) weaponDamage = 1;

        double baseDamage = event.getBaseDamage();

        event.setBaseDamage(weaponDamage);
        debug("Old tool damage: " + baseDamage + " New tool damage: " + weaponDamage, damager);

        //Set sharpness to 1.8 damage value
        double newSharpnessDamage = DamageUtils.getOldSharpnessDamage(event.getSharpnessLevel());
        debug("Old sharpness damage: " + event.getSharpnessDamage() + " New: " + newSharpnessDamage, damager);
        event.setSharpnessDamage(newSharpnessDamage);
    }

    private boolean isHolding(Material mat, String type){
        return mat.toString().endsWith("_" + type.toUpperCase());
    }

    private boolean isHolding(Material mat, String[] types){
        boolean hasAny = false;
        for(String type : types)
            if(isHolding(mat, type))
                hasAny = true;
        return hasAny;
    }
}
