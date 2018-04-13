package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WeaponDamages {

    private static Map<String, Double> damages;

    private static OCMMain plugin;

    static void initialise(OCMMain plugin){
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
