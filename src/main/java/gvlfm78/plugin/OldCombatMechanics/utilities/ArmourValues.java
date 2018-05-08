package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Map;

/**
 * Created by Rayzr522 on 6/14/16.
 */
public class ArmourValues {

    private static Map<String, Double> values;

    private static OCMMain plugin;

    static void initialise(OCMMain plugin){
        Messenger.debug("Initialised armour values");
        ArmourValues.plugin = plugin;
        reload();
    }

    public static void reload(){
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("old-armour-strength.strength");

        values = ConfigUtils.loadDoubleMap(section);
    }


    public static double getValue(Material mat){
        return values.getOrDefault(mat.name(), 0.0);
    }
}
