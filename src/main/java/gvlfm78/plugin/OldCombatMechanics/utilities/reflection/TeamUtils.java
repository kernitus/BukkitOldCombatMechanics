package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import kernitus.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

/**
 * From <a href="https://www.spigotmc.org/resources/1-9-anti-collision.28770/">1.9 anti-collision plugin by Mentrixx</a>
 * Modified by kernitus to work with Rayzr's Reflector utility
 * <p>
 * I've tried to contact the author but didn't get an answer after months,
 * and as the plugin has no license decided to take some of the code
 */
public class TeamUtils {
    private static ArrayList<Player> securePlayers = new ArrayList<>();
    private static Class<?> packetTeamClass = null;
    private static Field nameField = null;
    private static Field modeField = null;
    private static Field collisionRuleField = null;
    private static Field playersField = null;

    static{
        try{
            packetTeamClass = Reflector.Packets.getPacket(PacketType.PlayOut, "ScoreboardTeam");

            nameField = Reflector.getInaccessibleField(packetTeamClass, "a");
            modeField = Reflector.getInaccessibleField(packetTeamClass, "i");
            collisionRuleField = Reflector.getInaccessibleField(packetTeamClass, "f");
            playersField = Reflector.getInaccessibleField(packetTeamClass, "h");

        } catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public static synchronized void sendTeamPacket(Player player){
        try{
            Object packetTeamObject = packetTeamClass.newInstance();

            nameField.set(packetTeamObject, UUID.randomUUID().toString().substring(0, 15));
            modeField.set(packetTeamObject, 0);
            playersField.set(packetTeamObject, Collections.singletonList(player.getName()));

            changePacketCollisionType(packetTeamObject);

            if(!getSecurePlayers().contains(player))
                Reflector.Packets.sendPacket(player, packetTeamObject);

        } catch(Exception ex){
            ex.printStackTrace();
        }
    }

    private static void changePacketCollisionType(Object packetTeamObject) throws Exception{
        collisionRuleField.set(packetTeamObject, "never");
    }

    public static ArrayList<Player> getSecurePlayers(){
        return securePlayers;
    }
}