/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection

import kernitus.plugin.OldCombatMechanics.utilities.reflection.SpigotFunctionChooser.ExceptionalFunction.WrappedException

/**
 * Chooses a Spigot API function to use
 * Chooses a function to apply based on a test supplier, remembers the choice and only uses the corresponding
 * function in the future.
 *
 *
 * The branch to pick is determined during the *first execution of its [.apply] method!*.
 * This means that no matter how often the feature branch is invoked, it will never reconsider its choice.
 *
 * @param <T> the type of the entity to apply the function to
 * @param <U> the type of the extra parameter(s). Use [Object] if unused, or list of objects if multiple.
 * @param <R> the return type of the function
</R></U></T> */
class SpigotFunctionChooser<T, U, R>
/**
 * Creates a new [SpigotFunctionChooser], which chooses between two given functions.
 *
 * @param test        the test supplier that will be invoked to choose a branch
 * @param trueBranch  the branch to pick when then test is true
 * @param falseBranch the branch to pick when then test is false
 */(
    private val test: (T, U) -> Boolean,
    private val trueBranch: (T, U) -> R,
    private val falseBranch: (T, U) -> R
) {
    private var chosen: ((T, U) -> R)? = null

    /**
     * Applies the stored action to the given target and chooses what branch to use on the first call.
     *
     * @param target     the target to apply it to
     * @param parameters the extra parameters to pass to the function
     * @return the result of applying the function to the given target
     */
    fun apply(target: T, parameters: U = arrayOfNulls<Any>(0) as U): R {
        if (chosen == null) {
            synchronized(this) {
                if (chosen == null) {
                    chosen = if (test(target, parameters)) trueBranch else falseBranch
                }
            }
        }
        return chosen!!.invoke(target, parameters)
    }

    fun interface ExceptionalFunction<T, U, R> {
        /**
         * This method is the target of the functional interface where you can
         * write logic that might throw an exception.
         *
         * @param t the first function argument
         * @param u the second function argument
         * @return the function result
         */
        @Throws(Throwable::class)
        fun applyWithException(t: T, u: U): R

        /**
         * Invokes [applyWithException]. If an exception is thrown, it wraps
         * the exception into a [WrappedException].
         *
         * @param t the first function argument
         * @param u the second function argument
         * @return the function result
         * @throws WrappedException if any exception is thrown
         */
        fun apply(t: T, u: U): R {
            return try {
                applyWithException(t, u)
            } catch (e: Throwable) {
                throw WrappedException(e)
            }
        }

        class WrappedException(cause: Throwable?) : RuntimeException(cause)
    }

    companion object {
        /**
         * Creates a [SpigotFunctionChooser] that uses the success parameter when the action completes without an
         * exception and otherwise uses the failure parameter.
         *
         *
         * The action is, per the doc for [SpigotFunctionChooser] only called *once*.
         *
         * @param action  the action to invoke
         * @param success the branch to take when no exception occurs
         * @param failure the branch to take when an exception occurs
         * @param <T>     the type of the class containing the method
         * @param <U>     the type of the parameter(s)
         * @param <R>     the return type of the method
         * @return a [SpigotFunctionChooser] that picks the branch based on whether action threw an exception
        </R></U></T> */
        private fun <T, U, R> onException(
            action: ExceptionalFunction<T, U, R>, success: (T, U) -> R, failure: (T, U) -> R
        ): SpigotFunctionChooser<T, U, R> {
            return SpigotFunctionChooser(
                { t: T, u: U ->
                    try {
                        action.apply(t, u)
                        return@SpigotFunctionChooser true
                    } catch (e: WrappedException) {
                        return@SpigotFunctionChooser false
                    }
                }, success, failure
            )
        }

        /**
         * Calls the Spigot API method if possible, otherwise uses reflection to access same method.
         * Useful for API methods that were only added after a certain version. Caches chosen method for performance.
         *
         *
         *  Note: 1.16 is last version with Spigot-mapped fields, 1.17 with Spigot-mapped methods.
         *
         * @param apiCall A reference to the function that should be called
         * @param clazz   The class containing the function to be accessed via reflection
         * @param name    The name of the function to be accessed via reflection
         * @return A new instance of [SpigotFunctionChooser]
         */
        fun <T, U, R> apiCompatReflectionCall(
            apiCall: ExceptionalFunction<T, U, R>, clazz: Class<T>, name: String, vararg argTypes: String?
        ): SpigotFunctionChooser<T, U, R> {
            return onException(
                apiCall,
                { t, u -> apiCall.apply(t, u) },
                Reflector.memoiseMethodInvocation(clazz, name, *argTypes)
            )
        }

        /**
         * Calls the Spigot API method if possible, otherwise uses the provided function as a workaround.
         *
         *
         * This should be used to avoid reflection wherever possible, making the plugin more compatible.
         * Chosen method is cached for performance. Do not use method references as they are eagerly-bound in Java 8.
         *
         *
         * @param apiCall A reference to the function that should be called
         * @param altFunc A function that should instead be called if API method not available.
         * @return A new instance of [SpigotFunctionChooser]
         */
        fun <T, U, R> apiCompatCall(
            apiCall: ExceptionalFunction<T, U, R>, altFunc: (T, U) -> R
        ): SpigotFunctionChooser<T, U, R> {
            return onException(apiCall, { t, u -> apiCall.apply(t, u) }, altFunc)
        }
    }
}
