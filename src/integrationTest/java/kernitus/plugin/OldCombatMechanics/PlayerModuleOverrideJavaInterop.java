/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics;

import kernitus.plugin.OldCombatMechanics.api.OldCombatMechanicsAPI;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.entity.Player;

final class PlayerModuleOverrideJavaInterop {

    private PlayerModuleOverrideJavaInterop() {
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static void setOverridesWithNullValue(OldCombatMechanicsAPI api, Player player, String moduleName) {
        Map overrides = new HashMap();
        overrides.put(moduleName, null);
        api.setModuleOverridesForPlayer(player, overrides);
    }
}
