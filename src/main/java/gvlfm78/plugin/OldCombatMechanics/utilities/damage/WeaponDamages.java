package kernitus.plugin.OldCombatMechanics.utilities.damage;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

public class WeaponDamages {

    private static Map<String, Double> damages;

    private static OCMMain plugin;

    public static void initialise(OCMMain plugin){
        WeaponDamages.plugin = plugin;
        reload();
    }

    private static void reload(){
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("old-tool-damage.damages");

        damages = ConfigUtils.loadDoubleMap(section);
    }

    public static double getDamage(Material mat){
        return damages.getOrDefault(mat.name(), -1.0);
    }
}
