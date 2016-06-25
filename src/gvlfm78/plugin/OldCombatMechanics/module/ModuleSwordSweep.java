package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.Config;
import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.OCMSweepTask;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Created by Rayzr522 on 6/25/16.
 */
public class ModuleSwordSweep extends Module {

    public ModuleSwordSweep(OCMMain plugin) {
        super(plugin);
    }

    // Add when finished:
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamaged(EntityDamageByEntityEvent e) {
        World world = e.getDamager().getWorld();

        if (!(e.getDamager() instanceof Player)) {
            return;
        }

        Player p = (Player) e.getDamager();
        Material mat = p.getInventory().getItemInMainHand().getType();

        if (isHolding(mat, "sword") && Config.moduleEnabled("disable-sword-sweep", world)) {
            onSwordAttack(e, p, mat);
        }

    }

    private void onSwordAttack(EntityDamageByEntityEvent e, Player p, Material mat) {
        //Disable sword sweep

        int locHashCode = p.getLocation().hashCode(); // ATTACKER
        if (e.getDamage() == 1.0) {
            // Possibly a sword sweep attack
            if (sweepTask().swordLocations.contains(locHashCode)) {
                e.setCancelled(true);
            }
        } else {
            sweepTask().swordLocations.add(locHashCode);
        }

        ModuleOldToolDamage.onAttack(e);
    }

    private OCMSweepTask sweepTask() {
        return plugin.sweepTask();
    }

    private boolean isHolding(Material mat, String type) {
        return mat.toString().endsWith("_" + type.toUpperCase());
    }

}