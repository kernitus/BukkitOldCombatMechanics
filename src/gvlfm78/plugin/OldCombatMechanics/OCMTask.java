package kernitus.plugin.OldCombatMechanics;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Team;

import java.util.Collection;
import java.util.List;

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
		String name = p.getName();
		List<?> worlds = plugin.getConfig().getList("disable-player-collisions.worlds");
		if(worlds.isEmpty()||worlds.contains(w.getName())) {
			Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("ocmInternal");
			if (!team.getEntries().contains(name)) {
				team.addEntry(name);
			}
		}
	}
}