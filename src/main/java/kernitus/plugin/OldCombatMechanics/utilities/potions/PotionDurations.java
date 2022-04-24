/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.potions;

/**
 * Used to hold information on duration of drinkable & splash version of potions
 */
public class PotionDurations {

    private final GenericPotionDurations drinkable, splash;

    public PotionDurations(GenericPotionDurations drinkable, GenericPotionDurations splash){
        this.drinkable = drinkable;
        this.splash = splash;
    }

    public PotionDurations(int drinkableBase, int drinkableII, int drinkableExtended,
                           int splashBase, int splashII, int splashExtended){

        this(new GenericPotionDurations(drinkableBase, drinkableII, drinkableExtended),
                new GenericPotionDurations(splashBase, splashII, splashExtended));
    }

    public GenericPotionDurations getDrinkable(){
        return drinkable;
    }
    public GenericPotionDurations getSplash(){
        return splash;
    }
}
