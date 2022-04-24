/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.teams;

public enum CollisionRule {
    ALWAYS("always"),
    NEVER("never"),
    HIDE_FOR_OTHER_TEAMS("pushOtherTeams"),
    HIDE_FOR_OWN_TEAM("pushOwnTeam");

    private String name;

    CollisionRule(String name){
        this.name = name;
    }

    public String getName(){
        return name;
    }
}
