package gvlfm78.plugin.OldCombatMechanics.utilities;

/**
 * For all the math utilities that I needed which (for some reason) aren't in the Math class.
 *
 * @author Rayzr
 */
public class MathHelper {

    /**
     * Clamps a value between a minimum and a maximum.
     *
     * @param value The value to clamp.
     * @param min   The minimum value to clamp to.
     * @param max   The maximum value to clamp to.
     * @return The clamped value.
     */
    public static double clamp(double value, double min, double max) {
        double realMin = Math.min(min, max);
        double realMax = Math.max(min, max);

        if (value < realMin) {
            value = realMin;
        }

        if (value > realMax) {
            value = realMax;
        }

        return value;
    }

}
