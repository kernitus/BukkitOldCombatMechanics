package gvlfm78.plugin.OldCombatMechanics.utilities.potions;

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
