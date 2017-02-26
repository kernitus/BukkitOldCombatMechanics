package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.module.Module;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bukkit.event.HandlerList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Rayzr522 on 6/27/16.
 */
public class ModuleLoader {

	private static OCMMain plugin;

	private static List<Module> modules = new ArrayList<Module>();
	private static HashMap<Module, Boolean> enabledModules = null;

	public static void Initialise(OCMMain plugin) {
		ModuleLoader.plugin = plugin;
	}

	public static void ToggleModules() {

		if (enabledModules == null) {

			enabledModules = new HashMap<Module, Boolean>();

			for (Module module : modules) {
				enabledModules.put(module, module.isEnabled());
				SetState(module, module.isEnabled());
			}

		} else {

			for (Module module : modules) {
				if (!enabledModules.containsKey(module)) {
					enabledModules.put(module, module.isEnabled());
					SetState(module, module.isEnabled());
				} else if (module.isEnabled() != enabledModules.get(module)) {
					enabledModules.put(module, module.isEnabled());
					SetState(module, module.isEnabled());
				}
			}
		}
	}

	private static void SetState(Module module, boolean state) {
		if (state) {
			plugin.getServer().getPluginManager().registerEvents(module, plugin);
			Messenger.debug("Enabled " + module.getClass().getSimpleName());
		} else {
			HandlerList.unregisterAll(module);
			Messenger.debug("Disabled " + module.getClass().getSimpleName());
		}
	}

	public static void AddModule(Module module) {
		modules.add(module);
	}
	public static HashMap<Module, Boolean> getEnabledModules(){
		return enabledModules;
	}
}
