package kernitus.plugin.OldCombatMechanics.module;

import kernitus.plugin.OldCombatMechanics.OCMMain;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketAdapter;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketEvent;
import kernitus.plugin.OldCombatMechanics.utilities.packet.PacketManager;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.Reflector;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.TeamUtils;
import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.ClassType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ModulePlayerCollisions extends Module {

    private CollisionPacketListener collisionPacketListener = new CollisionPacketListener();

    public ModulePlayerCollisions(OCMMain plugin) {
        super(plugin, "disable-player-collisions");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerLogin(PlayerJoinEvent e) {
        TeamUtils.sendTeamPacket(e.getPlayer());
        PacketManager.getInstance().addListener(collisionPacketListener, e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent e) {
        TeamUtils.getSecurePlayers().remove(e.getPlayer());
    }

    private static class CollisionPacketListener extends PacketAdapter {

        private static final Class<?> targetClass = Reflector
                .getClass(ClassType.NMS, "PacketPlayOutScoreboardTeam");

        @Override
        public void onPacketSend(PacketEvent packetEvent) {
            if (packetEvent.getPacket().getPacketClass() != targetClass) {
                return;
            }

            try {
                // this is very prone to changes as it just *gets the field by name*. But not in my code.
                TeamUtils.changePacketCollisionType(packetEvent.getPacket().getNMSPacket());
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("An error occurred setting the collision rule", e);
            }
        }
    }
}
