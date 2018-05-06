package gvlfm78.plugin.OldCombatMechanics.utilities.reflection;

import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * From <a href="https://www.spigotmc.org/resources/1-9-anti-collision.28770/">1.9 anti-collision plugin by Mentrixx</a>
 * Modified by gvlfm78 to work with Rayzr's Reflector utility
 * <p>
 * I've tried to contact the author but didn't get an answer after months,
 * and as the plugin has no license decided to take some of the code
 */
public class TeamUtils {
    private static List<Player> securePlayers = new ArrayList<>();
    private static Constructor<?> packetScoreboardTeamConstructor;
    private static Field nameField;
    private static Field modeField;
    private static Field collisionRuleField;
    private static Field playersField;
    private static Map<Player, String> teamNameMap = new WeakHashMap<>();

    static {
        try {
            Class<?> packetTeamClass = Reflector.Packets.getPacket(PacketType.PlayOut, "ScoreboardTeam");
            packetScoreboardTeamConstructor = Reflector.getConstructor(packetTeamClass, 0);

            nameField = Reflector.getInaccessibleField(packetTeamClass, "a");
            modeField = Reflector.getInaccessibleField(packetTeamClass, "i");
            collisionRuleField = Reflector.getInaccessibleField(packetTeamClass, "f");
            playersField = Reflector.getInaccessibleField(packetTeamClass, "h");
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static synchronized void sendTeamPacket(Player player) {
        if (getSecurePlayers().contains(player)) {
            return;
        }

        try {
            Object packetTeamObject = packetScoreboardTeamConstructor.newInstance();

            String teamName = UUID.randomUUID().toString().substring(0, 15);
            teamNameMap.put(player, teamName);

            nameField.set(packetTeamObject, teamName);
            modeField.set(packetTeamObject, TeamAction.CREATE.getMinecraftId());
            playersField.set(packetTeamObject, Collections.singletonList(player.getName()));

            changePacketCollisionType(packetTeamObject);

            Reflector.Packets.sendPacket(player, packetTeamObject);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Sends a packet that disbands the formerly formed team to re-enable collision.
     * <p>
     * If the player hasn't gotten a {@link #sendTeamPacket(Player)} yet, this method does nothing.
     *
     * @param player the player to send it to
     */
    public static synchronized void sendTeamRemovePacket(Player player) {
        if (!teamNameMap.containsKey(player)) {
            return;
        }
        String teamName = teamNameMap.get(player);

        try {
            Object packetTeamObject = packetScoreboardTeamConstructor.newInstance();

            nameField.set(packetTeamObject, teamName);
            modeField.set(packetTeamObject, TeamAction.DISBAND.getMinecraftId());

            Reflector.Packets.sendPacket(player, packetTeamObject);

            teamNameMap.remove(player);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    /**
     * Sets the collision rule to never.
     *
     * @param packetTeamObject the packet to set it on
     * @throws ReflectiveOperationException if an error occurs
     */
    public static void changePacketCollisionType(Object packetTeamObject) throws ReflectiveOperationException {
        collisionRuleField.set(packetTeamObject, "never");
    }

    public static List<Player> getSecurePlayers() {
        return securePlayers;
    }

    /**
     * The different actions a {@code PacketPlayOutScoreboardTeam} packet can represent.
     */
    private enum TeamAction {
        CREATE(0),
        DISBAND(1);

        private int minecraftId;

        TeamAction(int minecraftId) {
            this.minecraftId = minecraftId;
        }

        public int getMinecraftId() {
            return minecraftId;
        }
    }
}