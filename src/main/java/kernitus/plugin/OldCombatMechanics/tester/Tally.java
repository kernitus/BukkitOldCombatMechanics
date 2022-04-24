/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.tester;

public class Tally {
    private int passed = 0;
    private int failed = 0;

    public void passed() {
        passed++;
    }

    public void failed() {
        failed++;
    }

    public int getPassed() {
        return passed;
    }

    public int getFailed() {
        return failed;
    }

    public int getTotal() {
        return passed + failed;
    }
}
