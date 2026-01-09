/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.damage;

import com.cryptomorin.xseries.XMaterial;
import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.ConfigUtils;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public class WeaponDamages {

    private static Map<String, Double> damages;

    private static OCMMain plugin;

    public static void initialise(OCMMain plugin) {
        WeaponDamages.plugin = plugin;
        reload();
    }

    private static void reload() {
        final ConfigurationSection section = plugin.getConfig().getConfigurationSection("old-tool-damage.damages");
        damages = ConfigUtils.loadDoubleMap(section);
    }

    public static double getDamage(Material mat) {
        final String name = mat.name().replace("GOLDEN", "GOLD").replace("WOODEN", "WOOD").replace("SHOVEL", "SPADE");
        return damages.getOrDefault(name, -1.0);
    }

    public static double getDamage(String key) {
        return damages.getOrDefault(key, -1.0);
    }

    public static Map<Material, Double> getMaterialDamages() {
        final Map<Material, Double> materialMap = new HashMap<>();
        damages.forEach((name, damage) -> {
            final String newName = name.replace("GOLD", "GOLDEN").replace("WOOD", "WOODEN").replace("SPADE", "SHOVEL");
            XMaterial.matchXMaterial(newName).ifPresent(xmaterial -> {
                final Material material = xmaterial.parseMaterial();
                if (material != null) {
                    materialMap.put(material, damage);
                }
            });
        });
        return materialMap;
    }
}
