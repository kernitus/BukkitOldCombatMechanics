
package gvlfm78.plugin.OldCombatMechanics.utilities;

public class ArrayUtils {

	public static String concatArray(Object[] arr, String filler) {

		if (arr.length == 0) {

		return "";

		}

		String output = "";
		for (int i = 0; i < arr.length - 1; i++) {

			output += arr[i].toString();
			output += filler;

		}

		output += arr[arr.length - 1].toString();

		return output;

	}

	public static Object[] removeFirst(Object[] arr) {

		if (arr.length <= 1) {

		return arr;

		}

		Object[] out = new Object[arr.length - 1];

		for (int i = 1; i < arr.length; i++) {

			out[i - 1] = arr[i];

		}

		return out;

	}

	public static Object[] removeLast(Object[] arr) {

		if (arr.length <= 1) {

			return arr;

		}

		Object[] out = new Object[arr.length - 1];

		for (int i = 0; i < arr.length - 1; i++) {

			out[i] = arr[i];

		}

		return out;

	}

}
