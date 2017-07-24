package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.MathHelper;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRegainHealthEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModulePlayerRegen extends Module {

    private Map<UUID, Long> healTimes = new HashMap<>();

    public ModulePlayerRegen(OCMMain plugin) {
        super(plugin, "old-player-regen");
    }

	@EventHandler(priority = EventPriority.HIGHEST)
    public void onRegen(EntityRegainHealthEvent e) {

        if (e.getEntityType() != EntityType.PLAYER || e.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED)
            return;

        final Player p = (Player) e.getEntity();

        if (!isEnabled(p.getWorld())) return;

        e.setCancelled(true);

        long currentTime = System.currentTimeMillis() / 1000;
        long lastHealTime = getLastHealTime(p);

        if(currentTime - lastHealTime < module().getLong("frequency"))
            return;

        double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();

        if (p.getHealth() < maxHealth) {
            p.setHealth(MathHelper.clamp(p.getHealth() + module().getInt("amount"), 0.0, maxHealth));
            healTimes.put(p.getUniqueId(), currentTime);
        }
        
        final float previousExh = p.getExhaustion();
        final float exhToApply = (float) module().getDouble("exhaustion");
        	
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            //This is because bukkit doesn't stop the exhaustion change when cancelling the event
            p.setExhaustion(previousExh + exhToApply);
            debug("Exhaustion before: " + previousExh + " Now: " + p.getExhaustion() + "Saturation: " + p.getSaturation(), p);
        },1L);
    }

    private long getLastHealTime(Player p) {

        if (!healTimes.containsKey(p.getUniqueId()))
            healTimes.put(p.getUniqueId(), System.currentTimeMillis() / 1000);

        return healTimes.get(p.getUniqueId());
    }
}
