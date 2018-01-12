package gvlfm78.plugin.OldCombatMechanics.utilities;

public class ArrayUtils {
     /**
     * Safely returns the last element of the array. This will return <code>null</code> if the array length is less than 1.
     *
     * @param array The array to retrieve the last element from.
     * @param <T>   The type of the array.
     * @return The last element in the array or <code>null</code>.
     */
    public static <T> T last(T[] array) {
        return array.length < 1 ? null : array[array.length - 1];
    }
}
