/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.apiSmokeTest;

import kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ApiSmokeTestPlugin extends JavaPlugin {

    private static final String KNOWN_CONFIGURABLE_MODULE = "sword-blocking";

    @Override
    public void onEnable() {
        RegisteredServiceProvider<OldCombatMechanicsAPI> registration =
            Bukkit.getServicesManager().getRegistration(OldCombatMechanicsAPI.class);
        if (registration == null) {
            throw new IllegalStateException("OldCombatMechanicsAPI service is not registered");
        }

        OldCombatMechanicsAPI api = registration.getProvider();
        if (!api.getModuleNames().contains(KNOWN_CONFIGURABLE_MODULE)) {
            throw new IllegalStateException("OldCombatMechanicsAPI is missing known configurable module "
                + KNOWN_CONFIGURABLE_MODULE);
        }
    }

    @SuppressWarnings("unused")
    private void verifyPlayerSpecificApiCompiles(OldCombatMechanicsAPI api, Player player) {
        api.forceEnableModuleForPlayer(player, KNOWN_CONFIGURABLE_MODULE);
        api.isModuleEnabledForPlayer(player, KNOWN_CONFIGURABLE_MODULE);
        api.clearModuleOverrideForPlayer(player, KNOWN_CONFIGURABLE_MODULE);
    }
}
