package kernitus.plugin.OldCombatMechanics;

import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.util.logging.Logger;

public class OCMMain extends JavaPlugin {

    protected OCMUpdateChecker updateChecker = new OCMUpdateChecker(this);
    private OCMConfigHandler CH = new OCMConfigHandler(this);
    Logger logger = getLogger();

    @Override
    public void onEnable() {

        //Checking for updates
        updateChecker.sendUpdateMessages(logger);

        PluginDescriptionFile pdfFile = this.getDescription();

        // Listeners and stuff
        getServer().getPluginManager().registerEvents((new OCMListener(this)), this);// Firing event listener

        getCommand("OldCombatMechanics").setExecutor(new OCMCommandHandler(this));// Firing commands listener

        // Setting up config.yml
        CH.setupConfigyml();

        // Initialize Config utility
        Config.Initialize(this);

        // Metrics
        try {
            MetricsLite metrics = new MetricsLite(this);
            metrics.start();
        } catch (IOException e) {
            // Failed to submit the stats
        }

        // Logging to console the correct enabling of OCM
        logger.info(pdfFile.getName() + " v" + pdfFile.getVersion() + " has been enabled correctly");

    }

    @Override
    public void onDisable() {

        PluginDescriptionFile pdfFile = this.getDescription();
        // Logging to console the disabling of Hotels
        logger.info(pdfFile.getName() + " v" + pdfFile.getVersion() + " has been disabled");
    }

    public void upgradeConfig() {

        CH.upgradeConfig();

    }

}