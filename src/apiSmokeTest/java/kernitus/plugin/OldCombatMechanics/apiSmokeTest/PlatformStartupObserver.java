/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.apiSmokeTest;

import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.lang.reflect.Field;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Independent observer: remains enabled when OCM rejects the server. */
public final class PlatformStartupObserver extends JavaPlugin {
    private final List<String> errors = new CopyOnWriteArrayList<>();
    private int requirementMessages;
    private int checks;
    private final Handler handler = new Handler() {
        @Override public void publish(LogRecord record) {
            if (record.getThrown() != null) errors.add(record.getMessage() + ": " + record.getThrown());
            if (String.valueOf(record.getMessage()).contains("OldCombatMechanics requires Paper")) requirementMessages++;
        }
        @Override public void flush() {}
        @Override public void close() {}
    };

    @Override public void onLoad() {
        Logger.getLogger("").addHandler(handler);
    }

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            final boolean expected = Boolean.parseBoolean(System.getProperty("ocm.platform.expected", "true"));
            try {
                final Plugin ocm = Bukkit.getPluginManager().getPlugin("OldCombatMechanics");
                require(ocm != null, "OCM was not loaded");
                require(ocm.isEnabled() == expected, "Unexpected enabled state");
                require(flag(ocm, "supportedPlatform") == expected, "Unexpected marker result");
                require(flag(ocm, "packetEventsLoaded") == expected, "Unexpected packet initialisation");
                require(flag(ocm, "storageInitialised") == expected, "Unexpected storage initialisation");
                if (expected) {
                    require(new File(ocm.getDataFolder(), "config.yml").isFile(), "Missing configuration");
                    require(Bukkit.getServicesManager().getRegistrations(ocm).size() > 0, "Missing public service");
                    Bukkit.getPluginManager().disablePlugin(ocm);
                    require(!ocm.isEnabled(), "Plugin remained enabled after shutdown");
                    require(!flag(ocm, "packetEventsLoaded"), "Packet shutdown did not finish");
                    require(!flag(ocm, "storageInitialised"), "Storage shutdown did not finish");
                } else {
                    final Class<?> packets = Class.forName(
                            "kernitus.plugin.OldCombatMechanics.lib.packetevents.api.PacketEvents", false,
                            ocm.getClass().getClassLoader());
                    require(packets.getMethod("getAPI").invoke(null) == null, "Rejected startup acquired a packet API");
                    require(HandlerList.getRegisteredListeners(ocm).isEmpty(), "Rejected startup registered listeners");
                    require(Bukkit.getScheduler().getPendingTasks().stream().noneMatch(task -> task.getOwner() == ocm),
                            "Rejected startup scheduled work");
                    require(!ocm.getDataFolder().exists(), "Rejected startup created plugin data");
                    require(Bukkit.getServicesManager().getRegistrations(ocm).isEmpty(), "Rejected startup registered services");
                    require(ocm.getClass().getMethod("getInstance").invoke(null) == null, "Rejected startup set the singleton");
                    require(requirementMessages == 1, "Expected exactly one Paper requirement message, got " + requirementMessages);
                }
                require(errors.isEmpty(), "Startup or shutdown errors: " + errors);
                write("PASS", "server=" + Bukkit.getVersion() + "\nexpectedPaper=" + expected + "\nchecks=" + checks + "\n");
            } catch (Throwable failure) {
                write("FAIL", failure.toString());
            } finally {
                Bukkit.shutdown();
            }
        }, 20L);
    }

    private static boolean flag(Plugin plugin, String name) throws Exception {
        final Field field = plugin.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(plugin);
    }

    private void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private void write(String result, String details) {
        try {
            Files.createDirectories(getDataFolder().toPath());
            Files.write(new File(getDataFolder(), "test-results.txt").toPath(), result.getBytes(StandardCharsets.UTF_8));
            Files.write(new File(getDataFolder(), "startup-details.txt").toPath(), details.getBytes(StandardCharsets.UTF_8));
            getLogger().info(result + ": " + details);
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    @Override public void onDisable() {
        Logger.getLogger("").removeHandler(handler);
    }
}
