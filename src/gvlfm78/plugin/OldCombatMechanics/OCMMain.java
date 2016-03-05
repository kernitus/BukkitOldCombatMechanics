package gvlfm78.plugin.OldCombatMechanics;

import java.io.IOException;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class OCMMain extends JavaPlugin{

	protected OCMUpdateChecker updateChecker;

	@Override
	public void onEnable(){
		this.updateChecker = new OCMUpdateChecker(this, "http://dev.bukkit.org/bukkit-plugins/oldcombatmechanics/files.rss");
		this.updateChecker.updateNeeded();
		if(getConfig().getBoolean("settings.checkForUpdates")){
			if(this.updateChecker.updateNeeded()){
				getLogger().info("An update of OldCombatMechanics to version " + this.updateChecker.getVersion()+"is available!");
				getLogger().info("Click here to download it:"+this.updateChecker.getLink());
			}
		}
		PluginDescriptionFile pdfFile = this.getDescription();
		//Listeners and stuff
		getServer().getPluginManager().registerEvents((new OCMListener(this)), this);//Firing event listener
		getCommand("OldCombatMechanics").setExecutor(new OCMCommandHandler(this));//Firing commands listener

		//Logging to console the correct enabling of OCM
		getLogger().info(pdfFile.getName()+" v"+pdfFile.getVersion()+ " has been enabled correctly");
		//Metrics
		try {
			Metrics metrics = new Metrics(this);
			metrics.start();
		} catch (IOException e) {
			// Failed to submit the stats :-(
		}
	}
	@Override
	public void onDisable(){

		PluginDescriptionFile pdfFile = this.getDescription();
		//Logging to console the disabling of Hotels
		getLogger().info(pdfFile.getName() + " v" + pdfFile.getVersion() + " has been disabled");
	}
}