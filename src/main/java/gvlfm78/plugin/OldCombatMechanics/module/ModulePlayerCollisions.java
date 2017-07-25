package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.TeamUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ModulePlayerCollisions extends Module {

	//private OCMTask task = new OCMTask(plugin);

	public ModulePlayerCollisions(OCMMain plugin) {
		super(plugin, "disable-player-collisions");

	}

	@EventHandler
	public void onPlayerLogin(PlayerJoinEvent e) {
		e.getPlayer().sendMessage("You logged in");
		TeamUtils.sendTeamPacket(e.getPlayer());
	}

	@EventHandler
	public void onPlayerQuit(PlayerQuitEvent e){
		TeamUtils.getSecurePlayers().remove(e.getPlayer());
	}

	/*@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerLogin(PlayerJoinEvent e) {

		Player p = e.getPlayer();
		World world = p.getWorld();

		if (isEnabled(world))
			task.addPlayerToScoreboard(p);
		else 
			task.removePlayerFromScoreboard(p);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onWorldChange(PlayerChangedWorldEvent e) {

		Player player = e.getPlayer();
		World world = player.getWorld();

		if (isEnabled(world))
			task.addPlayerToScoreboard(player);
		else 
			task.removePlayerFromScoreboard(player);
	}*/
}
