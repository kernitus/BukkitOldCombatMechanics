/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import kernitus.plugin.OldCombatMechanics.utilities.EventRegistry;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ModuleLoader {

    private static EventRegistry eventRegistry;
    private static final List<OCMModule> modules = new ArrayList<>();
    private static final Set<String> internalModules = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "modeset-listener",
            "attack-cooldown-tracker",
            "entity-damage-listener"
    )));

    public static void initialise(OCMMain plugin) {
        modules.clear();
        ModuleLoader.eventRegistry = new EventRegistry(plugin);
    }

    public static void toggleModules() {
        modules.forEach(module -> setState(module, module.isEnabled()));
    }

    private static void setState(OCMModule module, boolean state) {
        if (state) {
            if (eventRegistry.registerListener(module)) {
                Messenger.debug("Enabled " + module.getClass().getSimpleName());
            }
        } else {
            if (eventRegistry.unregisterListener(module)) {
                Messenger.debug("Disabled " + module.getClass().getSimpleName());
            }
        }
    }

    public static void addModule(OCMModule module) {
        modules.add(module);
    }

    public static List<OCMModule> getModules() {
        return modules;
    }

    public static @Nullable OCMModule getConfigurableModule(String moduleName) {
        final String normalised = normaliseModuleName(moduleName);
        if (internalModules.contains(normalised)) return null;

        return modules.stream()
                .filter(module -> normaliseModuleName(module.getConfigName()).equals(normalised))
                .findFirst()
                .orElse(null);
    }

    public static Set<String> getConfigurableModuleNames() {
        final Set<String> names = new HashSet<>();
        for (OCMModule module : modules) {
            final String name = normaliseModuleName(module.getConfigName());
            if (!internalModules.contains(name)) {
                names.add(name);
            }
        }
        return Collections.unmodifiableSet(names);
    }

    public static void notifyPlayerStateChanged(@NotNull Player player) {
        modules.forEach(module -> module.onModesetChange(player));
    }

    public static String normaliseModuleName(String moduleName) {
        return moduleName == null ? "" : moduleName.toLowerCase(Locale.ROOT).trim();
    }
}
