package gvlfm78.plugin.OldCombatMechanics;

import java.io.File;

import org.bukkit.configuration.file.YamlConfiguration;

public class OCMConfigHandler {
	private OCMMain plugin;

	public OCMConfigHandler(OCMMain instance) {
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
			
			//The following bit doesn't work and is awfully complicated so it's commented out for the future
			
			//Then we loop through the old version
			//Check if the value set for each node exists in the new one,
			//and if different, set the old value in the new one
			/*YamlConfiguration old = YamlConfiguration.loadConfiguration(backup);
			YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

			//Storing line after the comment and the comment itself
			Map<String, String> comments = new HashMap<String, String>();
			try{
				Scanner scanner = new Scanner(new FileInputStream(backup));

				for(int i = 0; scanner.hasNextLine(); i++){
					String line = scanner.nextLine();
					if(line.matches("\\s*#.*") || line.matches("^\\s*$")) //If line is a comment or blank
						comments.put(i, line); //Add it to the map of comment/blank lines
				}
				scanner.close();
			}
			catch(Exception e3){
				Messenger.err("Something went wrong while retreiving comments from config. Error:");
				e3.printStackTrace();
			}

			//Copy over the keys that are different between the two config files
			for(String key : old.getKeys(true)){
				//We don't want to copy over the old config-version or parent nodes
				if(key.equals("config-version") || old.get(key).toString().startsWith("MemorySection")) continue;

				if(!config.get(key).equals(old.get(key))){//If the old and new values don't match
					//Set the old value in the new config
					if(config.contains(key))
						config.set(key, old.get(key));
				}
			}
			//Save the new config using Bukkit's abysmal YAML implementation
			try {
				config.options().header(null);
				config.save(configFile);
			} catch (Exception e) {
				Messenger.err("Unable to copy values from old config to new. Error is: ");
				e.printStackTrace();
			}
			//Re-insert the comments
			try {
				Scanner scanner = new Scanner(new FileInputStream(configFile));

				Queue<String> newContents = new LinkedList<String>(); //This will contain the node-lines from the new config
				while(scanner.hasNextLine())
					newContents.add(scanner.nextLine());
				scanner.close();

				String finalContents = ""; //We'll be putting the whole file in this string before writing it out

				//Looping through until we don't have any more values in the queue
				for(int index = 0; !newContents.isEmpty(); index++){
					if(comments.containsKey(index)) //If we had a comment on that line in the previous file
						finalContents += comments.get(index) + "\n"; //Insert it here, then add a next line character
					else //Otherwise we put the next line from the new config and dequeue it
						finalContents += newContents.remove() + "\n";
				}

				//Now we write the string we made to the file
				Writer fileWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(configFile)));
				fileWriter.write(finalContents);
				fileWriter.close();
				Messenger.info("Successfully copied values from old config to new");
			} catch (Exception e1) {
				Messenger.err("Unable to insert comments from old config to new one. Error is:");
				e1.printStackTrace();
			}*/
		}
		plugin.saveDefaultConfig();
	}

	public void setupConfigyml() {
		if (!getFile("config.yml").exists())
			setupConfig("config.yml");
	}

	public void setupConfig(String fileName) {
		plugin.saveResource(fileName, false);
		plugin.getLogger().info("Config file " + fileName + " generated");
	}

	public YamlConfiguration getConfig(String fileName) {
		return YamlConfiguration.loadConfiguration(getFile(fileName));
	}

	public File getFile(String fileName) {
		return new File(plugin.getDataFolder() + File.separator + fileName);
	}
	public boolean doesConfigExist(){
		return getFile("config.yml").exists();
	}
}
