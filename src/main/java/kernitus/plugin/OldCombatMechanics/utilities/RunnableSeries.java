/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities;

import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.List;

public class RunnableSeries {
    private final List<BukkitRunnable> runnables;

    public RunnableSeries(BukkitRunnable... runnables){
        this.runnables = Arrays.asList(runnables);
    }

    public List<BukkitRunnable> getRunnables(){
        return runnables;
    }

    public void cancelAll(){
        runnables.forEach(BukkitRunnable::cancel);
    }
}
