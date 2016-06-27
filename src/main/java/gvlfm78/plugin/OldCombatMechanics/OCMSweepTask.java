package gvlfm78.plugin.OldCombatMechanics;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class OCMSweepTask extends BukkitRunnable {

	public OCMSweepTask() {}
	
	public List<Integer> swordLocations = new ArrayList<Integer>();

	public void run() {
		//Clearing buffer
		swordLocations.clear();
		
	}

}
