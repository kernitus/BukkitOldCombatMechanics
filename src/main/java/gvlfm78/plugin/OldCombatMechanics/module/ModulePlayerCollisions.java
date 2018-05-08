package gvlfm78.plugin.OldCombatMechanics.module;

import gvlfm78.plugin.OldCombatMechanics.OCMMain;
import gvlfm78.plugin.OldCombatMechanics.utilities.packet.PacketAdapter;
import gvlfm78.plugin.OldCombatMechanics.utilities.packet.PacketEvent;
import gvlfm78.plugin.OldCombatMechanics.utilities.packet.PacketManager;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.TeamUtils;
import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ModulePlayerCollisions extends Module {

    private CollisionPacketListener collisionPacketListener = new CollisionPacketListener();

    public ModulePlayerCollisions(OCMMain plugin){
        super(plugin, "disable-player-collisions");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerJoinEvent e){
        if(isEnabled(e.getPlayer().getWorld())){
            TeamUtils.sendTeamPacket(e.getPlayer());
        }

        // always attach the listener, it checks internally
        PacketManager.getInstance().addListener(collisionPacketListener, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangeWorld(PlayerChangedWorldEvent e){
        if(isEnabled(e.getPlayer().getWorld())){
            TeamUtils.sendTeamPacket(e.getPlayer());
        } else {
            TeamUtils.sendTeamRemovePacket(e.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e){
        TeamUtils.getSecurePlayers().remove(e.getPlayer());
    }

    private class CollisionPacketListener extends PacketAdapter {

        private final Class<?> targetClass = Reflector
                .getClass(ClassType.NMS, "PacketPlayOutScoreboardTeam");

        @Override
        public void onPacketSend(PacketEvent packetEvent){
            if(packetEvent.getPacket().getPacketClass() != targetClass){
                return;
            }

            if(!isEnabled(packetEvent.getPlayer().getWorld())){
                return;
            }

            try{
                // this is very prone to changes as it just *gets the field by name*. But not in my code.
                TeamUtils.changePacketCollisionType(packetEvent.getPacket().getNMSPacket());
            } catch(ReflectiveOperationException e){
                throw new RuntimeException("An error occurred setting the collision rule", e);
            }
        }
    }
}
