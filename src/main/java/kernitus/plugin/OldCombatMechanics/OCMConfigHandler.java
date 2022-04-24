/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class OCMConfigHandler {
    private OCMMain plugin;

    public OCMConfigHandler(OCMMain instance){
        this.plugin = instance;
    }

    public void upgradeConfig(){
        if(doesConfigExist()){
            //First we change name to the old config
            File backup = getFile("config-backup.yml");
            if(backup.exists()) backup.delete();
            File configFile = getFile("config.yml");
            configFile.renameTo(backup);
            //Then we save the new version
            plugin.saveResource("config.yml", false);
        }

        plugin.saveDefaultConfig();
    }

    public void setupConfig(){
        if(!getFile("config.yml").exists())
            setupConfig("config.yml");
    }

    private void setupConfig(String fileName){
        plugin.saveResource(fileName, false);
        plugin.getLogger().info("Config file " + fileName + " generated");
    }

    public YamlConfiguration getConfig(String fileName){
        return YamlConfiguration.loadConfiguration(getFile(fileName));
    }

    public File getFile(String fileName){
        return new File(plugin.getDataFolder(), fileName.replace('/', File.separatorChar));
    }

    public boolean doesConfigExist(){
        return getFile("config.yml").exists();
    }
}
