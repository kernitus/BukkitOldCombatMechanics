package gvlfm78.plugin.OldCombatMechanics;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.scheduler.BukkitRunnable;

public class OCMSweepTask extends BukkitRunnable {

	public OCMSweepTask() {}
	
	public List<Integer> swordLocations = new ArrayList<Integer>();
	
	@Override
	public void run() {
		//Clearing buffer
		swordLocations = null;
		
	}

}
