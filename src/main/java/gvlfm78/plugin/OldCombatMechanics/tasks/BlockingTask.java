package gvlfm78.plugin.OldCombatMechanics;

import gvlfm78.plugin.OldCombatMechanics.module.ModuleSwordBlocking;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BlockingTask extends BukkitRunnable {

    private Player p;

    public BlockingTask(Player p){
        this.p = p;
    }

    @Override
    public void run() {
        ModuleSwordBlocking.INSTANCE.restore(p);
    }
}
