package kernitus.plugin.OldCombatMechanics.tasks;

import kernitus.plugin.OldCombatMechanics.module.ModuleSwordBlocking;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class BlockingTask extends BukkitRunnable {

    private ModuleSwordBlocking module;
    private Player player;

    public BlockingTask(ModuleSwordBlocking module, Player player) {
        this.module = module;
        this.player = player;
    }

    @Override
    public void run() {
        module.restore(player);
    }
}
