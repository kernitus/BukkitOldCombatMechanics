package gvlfm78.plugin.OldCombatMechanics;

import java.util.Collection;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class OCMTask extends BukkitRunnable {

	OCMMain plugin;
	public OCMTask(OCMMain instance) {
		this.plugin = instance;
	}
	
	@Override
	public void run(){
		Collection<? extends Player> players = Bukkit.getServer().getOnlinePlayers();
		for(Player p : players){
			addPlayerToScoreboard(p);
		}
	}
	public void addPlayerToScoreboard(Player p){
		World w = p.getWorld();
		List<?> worlds = plugin.getConfig().getList("disable-player-collisions.worlds");
		if(worlds.contains(w.getName())){
			//Add player to scoreboard
		}
	}
}
