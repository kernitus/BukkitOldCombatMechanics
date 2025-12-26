/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.potions;

/**
 * Hold information on duration of drinkable & splash version of a potion type
 */
public final class PotionDurations {
    private final int drinkable;
    private final int splash;

    public PotionDurations(int drinkable, int splash) {
        this.drinkable = drinkable;
        this.splash = splash;
    }

    public int drinkable() {
        return drinkable;
    }

    public int splash() {
        return splash;
    }
}
