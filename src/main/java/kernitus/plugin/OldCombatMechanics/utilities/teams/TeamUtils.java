package kernitus.plugin.OldCombatMechanics.utilities.teams;

import kernitus.plugin.OldCombatMechanics.utilities.packet.team.TeamPacket;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contains useful methods when dealing with minecraft scoreboard teams.
 */
public class TeamUtils {

    private static final AtomicInteger TEAM_NAME_COUNTER = new AtomicInteger();

    /**
     * Checks if a team packet targets a player.
     *
     * @param packet the packet in question
     * @param player the player to check for
     * @return true if the packet targets the player
     */
    public static boolean targetsPlayer(TeamPacket packet, Player player){
        return packet.getPlayerNames().contains(player.getName());
    }

    /**
     * Creates a new team packet and sends it to the player. The team will have a random name.
     *
     * @param player        the player to send it to
     * @param collisionRule the collision rule to use
     */
    public static TeamPacket craftTeamCreatePacket(Player player, CollisionRule collisionRule){
        // Has the right size (10) and is unique
        String teamName = "OCM-" + TEAM_NAME_COUNTER.getAndIncrement() + "";

        return TeamPacket.create(
                TeamAction.CREATE,
                collisionRule,
                teamName,
                Collections.singletonList(player)
        );
    }

    /**
     * @param name the name of the team
     * @return true if the team was created by OCM
     */
    public static boolean isOcmTeam(TeamPacket team){
        return team.getName().startsWith("OCM-");
    }

    /**
     * Disbands a team.
     *
     * @param teamName the name of the team to disband
     * @param player   the player to disband it for
     */
    public static void disband(String teamName, Player player){
        TeamPacket packet = TeamPacket.create(
                TeamAction.DISBAND,
                CollisionRule.NEVER,
                teamName,
                Collections.singletonList(player)
        );
        packet.send(player);
    }
}
