package kernitus.plugin.OldCombatMechanics;

import java.io.IOException;
import java.util.HashMap;
import java.util.logging.Logger;

import org.bstats.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import com.codingforcookies.armourequip.ArmourListener;

import kernitus.plugin.OldCombatMechanics.module.Module;
import kernitus.plugin.OldCombatMechanics.module.ModuleAttackCooldown;
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableBowBoost;
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableElytra;
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableOffHand;
import kernitus.plugin.OldCombatMechanics.module.ModuleDisableProjectileRandomness;
import kernitus.plugin.OldCombatMechanics.module.ModuleFishingKnockback;
import kernitus.plugin.OldCombatMechanics.module.ModuleGoldenApple;
import kernitus.plugin.OldCombatMechanics.module.ModuleNoLapisEnchantments;
import kernitus.plugin.OldCombatMechanics.module.ModuleOldArmourStrength;
import kernitus.plugin.OldCombatMechanics.module.ModuleOldBrewingStand;
import kernitus.plugin.OldCombatMechanics.module.ModuleOldToolDamage;
import kernitus.plugin.OldCombatMechanics.module.ModulePlayerCollisions;
import kernitus.plugin.OldCombatMechanics.module.ModulePlayerRegen;
import kernitus.plugin.OldCombatMechanics.module.ModuleProjectileKnockback;
import kernitus.plugin.OldCombatMechanics.module.ModuleShieldCrafting;
import kernitus.plugin.OldCombatMechanics.module.ModuleSwordBlocking;
import kernitus.plugin.OldCombatMechanics.module.ModuleSwordSweep;
import kernitus.plugin.OldCombatMechanics.utilities.Config;
import kernitus.plugin.OldCombatMechanics.utilities.Messenger;

public class OCMMain extends JavaPlugin {

	Logger logger = getLogger();
	private OCMConfigHandler CH = new OCMConfigHandler(this);
	private OCMTask task = null;
	private OCMSweepTask sweepTask = null;
	private ItemStack gapple = new ItemStack(Material.GOLDEN_APPLE, 1, (short) 1);
	
	private ShapedRecipe r;
	
	@SuppressWarnings("deprecation")
	public OCMMain(){
		try{ 
			r = new ShapedRecipe(new NamespacedKey(this, "MINECRAFT"), gapple);
		}
		catch(NoClassDefFoundError e) {
			r = new ShapedRecipe(gapple);
		}
		r.shape("ggg", "gag", "ggg").setIngredient('g', Material.GOLD_BLOCK).setIngredient('a', Material.APPLE);
	}

	@Override
	public void onEnable() {
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

		// Disabling player collisions
		if (Config.moduleEnabled("disable-player-collisions"))
			// Even though it says "restart", it works for just starting it too
			restartTask();

		if (Config.moduleEnabled("disable-sword-sweep"))
			//Start up anti sword sweep attack task
			restartSweepTask();

		// Register crafting recipes
		registerCrafting();

		// MCStats Metrics
		try {
			MetricsLite metrics = new MetricsLite(this);
			metrics.start();
		} catch (IOException e) {
			// Failed to submit the stats
		}
		
		//BStats Metrics
		Metrics metrics = new Metrics(this);
		
		metrics.addCustomChart(new Metrics.SimpleBarChart("enabled_modules") {
			@Override
			public HashMap<String, Integer> getValues(HashMap<String, Integer> values) {
				for(Module module : ModuleLoader.getEnabledModules().keySet())
					if(module.isEnabled()) values.put(module.toString(), 1);
				return values;
			}
		});

		// Logging to console the enabling of OCM
		logger.info(pdfFile.getName() + " v" + pdfFile.getVersion() + " has been enabled correctly");

	}

	@Override
	public void onDisable() {

		PluginDescriptionFile pdfFile = this.getDescription();

		if (task != null) task.cancel();

		// Logging to console the disabling of OCM
		logger.info(pdfFile.getName() + " v" + pdfFile.getVersion() + " has been disabled");
	}

	private void registerAllEvents() {

		// Update Checker (also a module so we can use the dynamic registering/unregistering)
		ModuleLoader.AddModule(new OCMUpdateChecker(this, this.getFile()));

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
		ModuleLoader.AddModule(new ModuleShieldCrafting(this));
		ModuleLoader.AddModule(new ModuleDisableOffHand(this));
		ModuleLoader.AddModule(new ModuleOldBrewingStand(this));
		ModuleLoader.AddModule(new ModuleDisableElytra(this));
		ModuleLoader.AddModule(new ModuleDisableProjectileRandomness(this));
		ModuleLoader.AddModule(new ModuleDisableBowBoost(this));
		ModuleLoader.AddModule(new ModuleProjectileKnockback(this));
		ModuleLoader.AddModule(new ModuleNoLapisEnchantments(this));

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

	public void restartTask() {

		if (task == null)
			task = new OCMTask(this);
		else {
			task.cancel();
			task = new OCMTask(this);
		}

		double minutes = getConfig().getDouble("disable-player-collisions.collision-check-frequency");

		if (minutes > 0)
			task.runTaskTimerAsynchronously(this, 0, (long) minutes * 60 * 20);
		else
			task.runTaskTimerAsynchronously(this, 0, 60 * 20);

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

	private void registerCrafting() {

		if (!Config.moduleSettingEnabled("old-golden-apples", "no-conflict-mode")) {
			if (Bukkit.getRecipesFor(gapple).size() == 0)
				Bukkit.addRecipe(r);
		}
	}
}