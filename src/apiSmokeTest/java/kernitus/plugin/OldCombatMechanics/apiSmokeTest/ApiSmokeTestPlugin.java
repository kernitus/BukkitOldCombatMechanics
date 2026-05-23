/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.apiSmokeTest;

import kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI;
import kernitus.plugin.OldCombatMechanics.api.PlayerModesetChangeEvent;
import kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverride;
import kernitus.plugin.OldCombatMechanics.api.PlayerModuleOverrideChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;

public final class ApiSmokeTestPlugin extends JavaPlugin {

    private static final String KNOWN_CONFIGURABLE_MODULE = "sword-blocking";

    @Override
    public void onEnable() {
        try {
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
            Set<String> modesetNames = api.getModesetNames();
            if (modesetNames.isEmpty()) {
                throw new IllegalStateException("OldCombatMechanicsAPI did not expose any modesets");
            }
            World world = Bukkit.getWorlds().get(0);
            Set<String> allowedModesets = api.getAllowedModesets(world);
            if (allowedModesets.isEmpty()) {
                throw new IllegalStateException("OldCombatMechanicsAPI did not expose allowed modesets for "
                    + world.getName());
            }
            writeResult("PASS");
        } catch (Throwable throwable) {
            getLogger().severe("API smoke test failed: " + throwable.getMessage());
            try {
                writeResult("FAIL");
            } catch (IOException ioException) {
                getLogger().severe("Could not write API smoke test result: " + ioException.getMessage());
            }
        } finally {
            Bukkit.getScheduler().runTask(this, Bukkit::shutdown);
        }
    }

    private void writeResult(String result) throws IOException {
        File resultFile = new File(getDataFolder(), "test-results.txt");
        File parent = resultFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        Files.write(resultFile.toPath(), result.getBytes(StandardCharsets.UTF_8));
    }

    @SuppressWarnings("unused")
    private void verifyPlayerSpecificApiCompiles(OldCombatMechanicsAPI api, Player player) {
        api.forceEnableModuleForPlayer(player, KNOWN_CONFIGURABLE_MODULE);
        api.isModuleEnabledForPlayer(player, KNOWN_CONFIGURABLE_MODULE);
        api.clearModuleOverrideForPlayer(player, KNOWN_CONFIGURABLE_MODULE);
        api.setModuleOverridesForPlayer(player, java.util.Collections.singletonMap(
            KNOWN_CONFIGURABLE_MODULE,
            PlayerModuleOverride.FORCE_ENABLED
        ));
        String currentModeset = api.getModesetForPlayer(player);
        if (currentModeset != null) {
            api.setModesetForPlayer(player, currentModeset);
        }
        PlayerModesetChangeEvent modesetEvent = new PlayerModesetChangeEvent(
            player,
            player.getWorld(),
            currentModeset,
            currentModeset,
            PlayerModesetChangeEvent.Reason.API
        );
        modesetEvent.getReason();
        PlayerModuleOverrideChangeEvent overrideEvent = new PlayerModuleOverrideChangeEvent(
            player,
            KNOWN_CONFIGURABLE_MODULE,
            PlayerModuleOverride.DEFAULT,
            PlayerModuleOverride.FORCE_ENABLED
        );
        overrideEvent.getNewOverride();
    }
}
