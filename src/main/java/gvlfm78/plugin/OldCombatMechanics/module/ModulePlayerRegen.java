package kernitus.plugin.OldCombatMechanics.module;

import java.util.HashMap;
import java.util.UUID;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.MathHelper;

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

        long currentTime = System.currentTimeMillis()/1000;
        long lastHealTime = getLastHealTime(p);

        if(currentTime - lastHealTime < 3)
            return;

        if (p.getHealth() < p.getMaxHealth()) {
            p.setHealth(MathHelper.clamp(p.getHealth() + 1, 0.0, 20.0));
            healTimes.put(p.getUniqueId(), currentTime);
        }

    }

    private long getLastHealTime(Player p) {

        if (!healTimes.containsKey(p.getUniqueId()))
            healTimes.put(p.getUniqueId(), System.currentTimeMillis()/1000);

        return healTimes.get(p.getUniqueId());

    }

}
