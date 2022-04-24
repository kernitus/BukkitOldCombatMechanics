/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.teams;

/**
 * The different actions a {@code PacketPlayOutScoreboardTeam} packet can represent.
 */
public enum TeamAction {
    CREATE(0),
    DISBAND(1),
    UPDATE(2),
    ADD_PLAYER(3),
    REMOVE_PLAYER(4);

    private final int minecraftId;

    TeamAction(int minecraftId){
        this.minecraftId = minecraftId;
    }

    public int getMinecraftId(){
        return minecraftId;
    }

    public static TeamAction fromId(int id){
        for(TeamAction rule : values()){
            if(rule.getMinecraftId() == id){
                return rule;
            }
        }
        return null;
    }
}
