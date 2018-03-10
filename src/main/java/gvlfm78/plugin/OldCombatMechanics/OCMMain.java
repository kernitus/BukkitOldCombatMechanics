package kernitus.plugin.OldCombatMechanics;

import com.codingforcookies.armourequip.ArmourListener;
import kernitus.plugin.OldCombatMechanics.module.*;
import kernitus.plugin.OldCombatMechanics.updater.ModuleUpdateChecker;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;

public class OCMMain extends JavaPlugin {

	private Logger logger = getLogger();
	private OCMConfigHandler CH = new OCMConfigHandler(this);
	private OCMSweepTask sweepTask = null;
	private static OCMMain INSTANCE;

	@Override
	public void onEnable() {
	    INSTANCE = this;

		PluginDescriptionFile pdfFile = this.getDescription();

		// Setting up config.yml
		CH.setupConfigyml();

		// Initialise ModuleLoader utility
		ModuleLoader.Initialise(this);

		// Register every event class (as well as our command handler)
		registerAllEvents();

		// Initialise the Messenger utility
		Messenger.Initialise(this);

		// Initialise Config utility
		Config.Initialise(this);

		// Initialise the team if it doesn't already exist
		createTeam();

		// Disabling player collision
		/*if (Config.moduleEnabled("disable-player-collision"))
			// Even though it says "restart", it works for just starting it too
			restartTask();*/

		//Remove scoreboard
		String name = "ocmInternal";
		Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
		scoreboard.getTeam(name).unregister();

		if (Config.moduleEnabled("disable-sword-sweep"))
			//Start up anti sword sweep attack task
			restartSweepTask();

		// MCStats Metrics
		try {
			MetricsLite metrics = new MetricsLite(this);
			metrics.start();
		} catch (IOException e) {
			// Failed to submit the stats
		}

		//BStats Metrics
		Metrics metrics = new Metrics(this);

		metrics.addCustomChart(new Metrics.SimpleBarChart("enabled_modules", () -> {
			HashMap<String, Integer> values = new HashMap<>();
			ModuleLoader.getEnabledModules().keySet().forEach(module -> {
				if(module.isEnabled()) values.put(module.toString(), 1);
			});
			return values;
		}));

		// Logging to console the enabling of OCM
		logger.info(pdfFile.getName() + " v" + pdfFile.getVersion() + " has been enabled");

	}

	@Override
	public void onDisable() {

		PluginDescriptionFile pdfFile = this.getDescription();

		//if (task != null) task.cancel();

		// Logging to console the disabling of OCM
		logger.info(pdfFile.getName() + " v" + pdfFile.getVersion() + " has been disabled");
	}

	private void registerAllEvents() {

		// Update Checker (also a module so we can use the dynamic registering/unregistering)
		ModuleLoader.AddModule(new ModuleUpdateChecker(this, this.getFile()));

		// Module listeners
		ModuleLoader.AddModule(new ArmourListener(this));
		ModuleLoader.AddModule(new ModuleAttackCooldown(this));
		ModuleLoader.AddModule(new ModulePlayerCollisions(this));

		//Apparently listeners registered after get priority
		ModuleLoader.AddModule(new ModuleOldToolDamage(this));
		ModuleLoader.AddModule(new ModuleSwordSweep(this));

		ModuleLoader.AddModule(new ModuleGoldenApple(this));
		ModuleLoader.AddModule(new ModuleFishingKnockback(this));
		ModuleLoader.AddModule(new ModulePlayerRegen(this));
		ModuleLoader.AddModule(new ModuleSwordBlocking(this));
		ModuleLoader.AddModule(new ModuleOldArmourStrength(this));
		ModuleLoader.AddModule(new ModuleDisableCrafting(this));
		ModuleLoader.AddModule(new ModuleDisableOffHand(this));
		ModuleLoader.AddModule(new ModuleOldBrewingStand(this));
		ModuleLoader.AddModule(new ModuleDisableElytra(this));
		ModuleLoader.AddModule(new ModuleDisableProjectileRandomness(this));
		ModuleLoader.AddModule(new ModuleDisableBowBoost(this));
		ModuleLoader.AddModule(new ModuleProjectileKnockback(this));
		ModuleLoader.AddModule(new ModuleNoLapisEnchantments(this));
		ModuleLoader.AddModule(new ModuleDisableEnderpearlCooldown(this));

		getCommand("OldCombatMechanics").setExecutor(new OCMCommandHandler(this, this.getFile()));// Firing commands listener
	}

	private void createTeam() {
		String name = "ocmInternal";
		Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

		Team team = null;

		for (Team t : scoreboard.getTeams()) {
			if (t.getName().equals(name)) {
				team = t;
				break;
			}
		}

		if (team == null)
			team = scoreboard.registerNewTeam(name);

		team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.FOR_OWN_TEAM);
		team.setAllowFriendlyFire(true);
	}

	public void upgradeConfig() {
		CH.upgradeConfig();
	}

	public boolean doesConfigymlExist() {
		return CH.doesConfigymlExist();
	}

	public static OCMMain getInstance(){
	    return INSTANCE;
    }

	public void restartSweepTask() {
		if (sweepTask == null)
			sweepTask = new OCMSweepTask();
		else {
			sweepTask.cancel();
			sweepTask = new OCMSweepTask();
		}
		sweepTask.runTaskTimer(this, 0, 1);
	}

	public OCMSweepTask sweepTask() {
		return sweepTask;
	}
}