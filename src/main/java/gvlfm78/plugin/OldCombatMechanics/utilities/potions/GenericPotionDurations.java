package kernitus.plugin.OldCombatMechanics.utilities.potions;

/**
 * Used to hold information on duration of base, II & extended versions of potion
 */
public class GenericPotionDurations {

    private final int base, II, extended;

    public GenericPotionDurations(int base, int II, int extended){
        this.base = base;
        this.II = II;
        this.extended = extended;
    }

    public int getBaseTime(){
        return base;
    }

    public int getIITime(){
        return II;
    }

    public int getExtendedTime(){
        return extended;
    }
}
