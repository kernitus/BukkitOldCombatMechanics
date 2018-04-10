package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.module.Module;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Rayzr522 on 6/27/16.
 */
public class ModuleLoader {

	private static OCMMain plugin;

	private static List<Module> modules = new ArrayList<>();
	private static Map<Module, Boolean> enabledModules = null;

	public static void initialise(OCMMain plugin) {
		ModuleLoader.plugin = plugin;
	}

	public static void toggleModules() {

		if (enabledModules == null) {

			enabledModules = new HashMap<>();

			for (Module module : modules) {
				enabledModules.put(module, module.isEnabled());
				setState(module, module.isEnabled());
			}

		} else {

			for (Module module : modules) {

				if (!enabledModules.containsKey(module)) {
					enabledModules.put(module, module.isEnabled());
					setState(module, module.isEnabled());
				} else if (module.isEnabled() != enabledModules.get(module)) {
					enabledModules.put(module, module.isEnabled());
					setState(module, module.isEnabled());
				}

			}
		}
	}

	private static void setState(Module module, boolean state) {
		if (state) {
			plugin.getServer().getPluginManager().registerEvents(module, plugin);
			Messenger.debug("Enabled " + module.getClass().getSimpleName());
		} else {
			HandlerList.unregisterAll(module);
			Messenger.debug("Disabled " + module.getClass().getSimpleName());
		}
	}

	public static void addModule(Module module) {
		modules.add(module);
	}
	public static Map<Module, Boolean> getEnabledModules(){
		return enabledModules;
	}
}
