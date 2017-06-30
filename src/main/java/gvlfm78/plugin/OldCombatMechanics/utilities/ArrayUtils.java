package gvlfm78.plugin.OldCombatMechanics.utilities;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;

public class ArrayUtils {
    /**
     * Joins the elements of an array with the given filler.
     *
     * @param array  The array to concatenate.
     * @param filler The filler to use.
     * @return The concatenated string.
     */
    public static String concatArray(Object[] array, String filler) {
        return Arrays.stream(array).map(Objects::toString).collect(Collectors.joining(filler));
    }

    /**
     * Removes the first item from an array unless it is the only item.
     *
     * @param array The array to modify.
     * @param <T>   The type of the array.
     * @return The modified array.
     */
    public static <T> T[] removeFirst(T[] array) {
        if (array.length <= 1) {
            return array;
        }

        return Arrays.copyOfRange(array, 1, array.length);
    }

    /**
     * Removes the last item from an array unless it is the only item.
     *
     * @param array The array to modify.
     * @param <T>   The type of the array.
     * @return The modified array.
     */
    public static <T> T[] removeLast(T[] array) {
        if (array.length <= 1) {
            return array;
        }

        return Arrays.copyOfRange(array, 0, array.length - 1);
    }

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
