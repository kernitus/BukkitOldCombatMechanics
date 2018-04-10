package kernitus.plugin.OldCombatMechanics.utilities;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WeaponDamages {

    private static Map<String, Double> damages;

    private static OCMMain plugin;

    public static void initialise(OCMMain plugin){
        WeaponDamages.plugin = plugin;
        reload();
    }

    private static void reload(){
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("old-tool-damage.damages");

        damages = section.getKeys(false).stream()
                .filter(((Predicate<String>) section::isDouble).or(section::isInt))
                // .peek(key -> Messenger.debug("[WeaponDamages] Loading damage '" + section.getDouble(key) + "' for type '" + key + "'"))
                .collect(Collectors.toMap(key -> key, section::getDouble));
    }

    public static double getDamage(Material mat){
        return damages.getOrDefault(mat.name(), -1.0);
    }
}
