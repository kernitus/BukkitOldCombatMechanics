package kernitus.plugin.OldCombatMechanics;

import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

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

    private void createTeam() {

        String name = "oldCombatMechanicsInternal";
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();

        Team team = null;

        for (Team t : scoreboard.getTeams()) {
            if (t.getName().equals(name)) {
                team = t;
                break;
            }
        }

        if (team == null) {
            team = scoreboard.registerNewTeam(name);
        }

        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);

    }

    public void upgradeConfig(){
        CH.upgradeConfig();
    }
    public boolean doesConfigymlExist(){
    	return CH.doesConfigymlExist();
    }
}