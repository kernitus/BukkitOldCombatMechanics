package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.MathHelper;
import me.vagdedes.spartan.system.Enums;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Establishes custom health regeneration rules.
 */
public class ModulePlayerRegen extends Module {

    private final Map<UUID, Long> healTimes = new HashMap<>();
    private boolean spartanInstalled;

    public ModulePlayerRegen(OCMMain plugin){
        super(plugin, "old-player-regen");

        initSpartan();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRegen(EntityRegainHealthEvent e){

        if(e.getEntityType() != EntityType.PLAYER || e.getRegainReason() != EntityRegainHealthEvent.RegainReason.SATIATED){
            return;
        }

        final Player p = (Player) e.getEntity();

        if(!isEnabled(p.getWorld())) return;

        e.setCancelled(true);

        long currentTime = System.currentTimeMillis() / 1000;
        long lastHealTime = getLastHealTime(p);

        if(currentTime - lastHealTime < module().getLong("frequency"))
            return;

        double maxHealth = p.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();

        if(p.getHealth() < maxHealth){
            p.setHealth(MathHelper.clamp(p.getHealth() + module().getInt("amount"), 0.0, maxHealth));
            healTimes.put(p.getUniqueId(), currentTime);

            disableSpartanRegenCheck(p);
        }

        final float previousExhaustion = p.getExhaustion();
        final float exhaustionToApply = (float) module().getDouble("exhaustion");

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            //This is because bukkit doesn't stop the exhaustion change when cancelling the event
            p.setExhaustion(previousExhaustion + exhaustionToApply);
            debug("Exhaustion before: " + previousExhaustion + " Now: " + p.getExhaustion() + " Saturation: " + p.getSaturation(), p);
        }, 1L);
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e){
        healTimes.remove(e.getPlayer().getUniqueId());
    }

    private long getLastHealTime(Player p){
        return healTimes.computeIfAbsent(p.getUniqueId(), id -> System.currentTimeMillis() / 1000);
    }

    private void disableSpartanRegenCheck(Player player){
        if(!spartanInstalled){
            return;
        }

        int ticksToCancel = plugin.getConfig().getInt("support.spartan-cancel-ticks", 1);
        me.vagdedes.spartan.api.API.cancelCheck(player, Enums.HackType.FastHeal, ticksToCancel);
    }

    private void initSpartan(){
        spartanInstalled = Bukkit.getPluginManager().getPlugin("Spartan") != null;
    }
}
