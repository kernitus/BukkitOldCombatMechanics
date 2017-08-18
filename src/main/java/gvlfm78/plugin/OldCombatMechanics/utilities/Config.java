package gvlfm78.plugin.OldCombatMechanics.utilities;

import gvlfm78.plugin.OldCombatMechanics.ModuleLoader;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.module.*;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Rayzr522 on 6/14/16.
 */

public class Config {

	private static OCMMain plugin;
	private static FileConfiguration config;
	private static List<Material> interactive = new ArrayList<>();

	public static void Initialise(OCMMain plugin) {

		Config.plugin = plugin;
		config = plugin.getConfig();

		if (!checkConfigVersion())
			load();

		reload();
	}


	private static boolean checkConfigVersion() {
		YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(plugin.getResource("config.yml")));

		if (config.getInt("config-version") != defaultConfig.getInt("config-version")) {

			plugin.getLogger().warning("Config version does not match, backing up old config and creating a new one");
			plugin.upgradeConfig();
			reload();
			return true;
		}

		return false;
	}


	public static void reload() {
		if (plugin.doesConfigymlExist()) {
			plugin.reloadConfig();
			config = plugin.getConfig();
		} else
			plugin.upgradeConfig();

		checkConfigVersion();

		//plugin.restartTask(); //Restart no-collision check
		plugin.restartSweepTask(); //Restart sword sweep check
		load();

		//Setting correct attack speed and armour values for online players
		for(World world : Bukkit.getWorlds()){

			List<Player> players = world.getPlayers();

			double GAS = plugin.getConfig().getDouble("disable-attack-cooldown.generic-attack-speed");

			if (!Config.moduleEnabled("disable-attack-cooldown", world))
				GAS = 4; //If module is disabled, set attack speed to 1.9 default

			boolean isArmourEnabled = Config.moduleEnabled("old-armour-strength", world);

			for(Player player : players){
				//Setting attack speed
				AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_ATTACK_SPEED);
				double baseValue = attribute.getBaseValue();

				if(baseValue!=GAS){
					attribute.setBaseValue(GAS);
					player.saveData();
				}

				//Setting armour values
				ModuleOldArmourStrength moas = new ModuleOldArmourStrength(plugin);
				moas.setArmourAccordingly(player, isArmourEnabled);
			}
		}
		if(Config.moduleEnabled("disable-offhand"))
			ModuleDisableOffHand.INSTANCE.reloadList();
		if(Config.moduleEnabled("old-golden-apples"))
			ModuleGoldenApple.INSTANCE.reloadRecipes();
		if(Config.moduleEnabled("sword-blocking") || Config.moduleEnabled("disable-elytra"))
			reloadInteractiveBlocks();
		if(Config.moduleEnabled("sword-blocking"))
			ModuleSwordBlocking.INSTANCE.reload();
		if(moduleEnabled("disable-crafting"))
			ModuleDisableCrafting.INSTANCE.reload();
	}

	private static void load() {

		Messenger.DEBUG_ENABLED = config.getBoolean("debug.enabled");

		WeaponDamages.Initialise(plugin); //Reload weapon damages from config
		ArmourValues.Initialise(plugin); //Reload armour values from config

		ModuleLoader.ToggleModules();

	}

	public static boolean moduleEnabled(String name, World world) {

		ConfigurationSection section = config.getConfigurationSection(name);

		if (section == null) {
			System.err.println("Tried to check module '" + name + "', but it didn't exist!");
			return false;
		}

		if (section.getBoolean("enabled")) {

			List<?> list = section.getList("worlds");

			if (world != null && list != null && list.size() > 0){
				for(Object wname : list){
					if(String.valueOf(wname).equalsIgnoreCase(world.getName()))
						return true;
				}
				return false;
			}
			return true;
		}

		return false;
	}

	public static boolean moduleEnabled(String name) {
		return moduleEnabled(name, null);
	}

	public static boolean debugEnabled() {
		return moduleEnabled("debug", null);
	}

	public static List<?> getWorlds(String moduleName) {
		return config.getList(moduleName + ".worlds");
	}

	public static boolean moduleSettingEnabled(String moduleName, String moduleSettingName) {

		return config.getBoolean(moduleName + "." + moduleSettingName);

	}

	public static void setModuleSetting(String moduleName, String moduleSettingName, boolean value) {

		config.set(moduleName + "." + moduleSettingName, value);
		plugin.saveConfig();

	}

	public static void reloadInteractiveBlocks(){

		List<String> list = config.getStringList("interactive");
		if(list==null) return;

		for(String name : list){
			Material mat = Material.matchMaterial(name);
			if(mat!=null)
				interactive.add(mat);
		}
	}

	public static List<Material> getInteractiveBlocks(){
		return interactive;
	}

}
