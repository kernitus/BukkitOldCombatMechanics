package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.MathHelper;
import kernitus.plugin.OldCombatMechanics.utilities.Ticks;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.HashMap;
import java.util.UUID;

/**
 * Created by Rayzr522 on 6/28/16.
 */
public class ModulePlayerRegen extends Module {

    private HashMap<UUID, Long> healTimes = new HashMap<UUID, Long>();

    public ModulePlayerRegen(OCMMain plugin) {
        super(plugin, "old-player-regen");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRegen(EntityRegainHealthEvent e) {

        if (e.getEntityType() != EntityType.PLAYER || e.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED) {
            return;
        }

        Player p = (Player) e.getEntity();

        if (!isEnabled(p.getWorld())) {
            return;
        }

        e.setCancelled(true);

        System.out.println("ModulePlayerRegen.onRegen");

        long currentTime = Ticks.current();
        long lastHealTime = getLastHealTime(p);

        System.out.println("currentTime = " + currentTime);
        System.out.println("lastHealTime = " + lastHealTime);

        if (currentTime - lastHealTime < 60) {
            return;
        }

        System.out.println("Regening the player...");

        System.out.println("(p.getHealth()<p.getMaxHealth()) = " + (p.getHealth() < p.getMaxHealth()));

        if (p.getHealth() < p.getMaxHealth()) {
            p.setHealth(MathHelper.clamp(p.getHealth() + 1, 0.0, 20.0));
            healTimes.put(p.getUniqueId(), currentTime);
        }

    }

    private long getLastHealTime(Player p) {

        if (!healTimes.containsKey(p.getUniqueId())) {
            healTimes.put(p.getUniqueId(), Ticks.current());
        }

        return healTimes.get(p.getUniqueId());

    }

}
