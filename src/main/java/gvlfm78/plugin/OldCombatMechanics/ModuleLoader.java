package gvlfm78.plugin.OldCombatMechanics;

import gvlfm78.plugin.OldCombatMechanics.module.Module;
import gvlfm78.plugin.OldCombatMechanics.utilities.EventRegistry;
import gvlfm78.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Rayzr522 on 6/27/16.
 */
public class ModuleLoader {

	private static EventRegistry eventRegistry;
	private static List<Module> modules = new ArrayList<>();

	public static void initialise(OCMMain plugin) {
		ModuleLoader.eventRegistry = new EventRegistry(plugin);
	}

	public static void toggleModules() {
        modules.forEach(module -> setState(module, module.isEnabled()));
	}

	private static void setState(Module module, boolean state) {
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

	public static void addModule(Module module) {
		modules.add(module);
	}

	public static List<Module> getModules(){
		return modules;
	}
}
