package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.MathHelper;
import gvlfm78.plugin.OldCombatMechanics.utilities.Ticks;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

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

        long currentTime = Ticks.current(p);
        long lastHealTime = getLastHealTime(p);

        if (currentTime - lastHealTime < 60) {
            return;
        }

        if (p.getHealth() < p.getMaxHealth()) {
            p.setHealth(MathHelper.clamp(p.getHealth() + 1, 0.0, 20.0));
            healTimes.put(p.getUniqueId(), currentTime);
        }

    }

    @EventHandler(priority =  EventPriority.HIGH)
    public void onWorldChange(PlayerChangedWorldEvent e) {

        if (healTimes.containsKey(e.getPlayer().getUniqueId())) {
            healTimes.remove(e.getPlayer().getUniqueId());
        }

    }

    private long getLastHealTime(Player p) {

        if (!healTimes.containsKey(p.getUniqueId())) {
            healTimes.put(p.getUniqueId(), Ticks.current(p));
        }

        return healTimes.get(p.getUniqueId());

    }

}
