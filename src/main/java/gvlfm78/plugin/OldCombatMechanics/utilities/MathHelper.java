package kernitus.plugin.OldCombatMechanics.utilities;

/**
 * Created by Rayzr522 on 6/28/16.
 * For all the math that I needed which (for some reason) isn't in the Math class
 */
public class MathHelper {

    public static float clamp(float value, float min, float max) {

        float min2 = Math.min(min, max);
        float max2 = Math.max(min, max);

        if (value < min2) {
            value = min2;
        }

        if (value > max2) {
            value = max2;
        }

        return value;

    }
    public static double clamp(double value, double min, double max) {

        double min2 = Math.min(min, max);
        double max2 = Math.max(min, max);

        if (value < min2) {
            value = min2;
        }

        if (value > max2) {
            value = max2;
        }

        return value;

    }

    public static int clamp(int value, int min, int max) {

        int min2 = Math.min(min, max);
        int max2 = Math.max(min, max);

        if (value < min2) {
            value = min2;
        }

        if (value > max2) {
            value = max2;
        }

        return value;

    }
    

}
