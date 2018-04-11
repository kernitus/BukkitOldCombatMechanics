package gvlfm78.plugin.OldCombatMechanics.utilities.reflection;

import gvlfm78.plugin.OldCombatMechanics.utilities.reflection.type.PacketType;
import org.bukkit.entity.Player;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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

    static{
        try{
            Class<?> packetTeamClass = Reflector.Packets.getPacket(PacketType.PlayOut, "ScoreboardTeam");
            packetScoreboardTeamConstructor = Reflector.getConstructor(packetTeamClass, 0);

            nameField = Reflector.getInaccessibleField(packetTeamClass, "a");
            modeField = Reflector.getInaccessibleField(packetTeamClass, "i");
            collisionRuleField = Reflector.getInaccessibleField(packetTeamClass, "f");
            playersField = Reflector.getInaccessibleField(packetTeamClass, "h");
        } catch(Exception ex){
            ex.printStackTrace();
        }
    }

    public static synchronized void sendTeamPacket(Player player){
        if(getSecurePlayers().contains(player)){
            return;
        }

        try{
            Object packetTeamObject = packetScoreboardTeamConstructor.newInstance();

            nameField.set(packetTeamObject, UUID.randomUUID().toString().substring(0, 15));
            modeField.set(packetTeamObject, 0);
            playersField.set(packetTeamObject, Collections.singletonList(player.getName()));

            changePacketCollisionType(packetTeamObject);

            Reflector.Packets.sendPacket(player, packetTeamObject);
        } catch(Exception ex){
            ex.printStackTrace();
        }
    }

    private static void changePacketCollisionType(Object packetTeamObject) throws Exception{
        collisionRuleField.set(packetTeamObject, "never");
    }

    public static List<Player> getSecurePlayers(){
        return securePlayers;
    }
}