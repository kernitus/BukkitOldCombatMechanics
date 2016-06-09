package kernitus.plugin.OldCombatMechanics;

import java.io.IOException;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class OCMMain extends JavaPlugin{

	protected OCMUpdateChecker updateChecker = new OCMUpdateChecker(this);
	private OCMConfigHandler CH = new OCMConfigHandler(this);

	@Override
	public void onEnable(){
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

		//Setting up config.yml
		CH.setupConfigyml();
		
		//Logging to console the correct enabling of OCM
		getLogger().info(pdfFile.getName()+" v"+pdfFile.getVersion()+ " has been enabled correctly");
		//Metrics
		try {
			MetricsLite metrics = new MetricsLite(this);
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