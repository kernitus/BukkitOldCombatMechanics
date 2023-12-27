/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.module.OCMModule;
import kernitus.plugin.OldCombatMechanics.utilities.EventRegistry;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;

import java.util.ArrayList;
import java.util.List;

public class ModuleLoader {

    private static EventRegistry eventRegistry;
    private static final List<OCMModule> modules = new ArrayList<>();

    public static void initialise(OCMMain plugin) {
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
}
