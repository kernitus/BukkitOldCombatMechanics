/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import java.util.function.BiFunction;

/**
 * Chooses a Spigot API function to use
 * Chooses a function to apply based on a test supplier, remembers the choice and only uses the corresponding
 * function in the future.
 * <p>
 * The branch to pick is determined during the <em>first execution of its {@link #apply(Object, Object)} method!</em>.
 * This means that no matter how often the feature branch is invoked, it will never reconsider its choice.
 *
 * @param <T> the type of the entity to apply the function to
 * @param <U> the type of the extra parameter(s). Use {@link Object} if unused, or list of objects if multiple.
 * @param <R> the return type of the function
 */
public class SpigotFunctionChooser<T, U, R> {

    private final BiFunction<T, U, Boolean> test;
    private final BiFunction<T, U, R> trueBranch;
    private final BiFunction<T, U, R> falseBranch;
    private BiFunction<T, U, R> chosen;

    /**
     * Creates a new {@link SpigotFunctionChooser}, which chooses between two given functions.
     *
     * @param test        the test supplier that will be invoked to choose a branch
     * @param trueBranch  the branch to pick when then test is true
     * @param falseBranch the branch to pick when then test is false
     */
    public SpigotFunctionChooser(BiFunction<T, U, Boolean> test, BiFunction<T, U, R> trueBranch, BiFunction<T, U, R> falseBranch) {
        this.test = test;
        this.trueBranch = trueBranch;
        this.falseBranch = falseBranch;
    }

    /**
     * Applies the stored action to the given target and chooses what branch to use on the first call.
     *
     * @param target     the target to apply it to
     * @param parameters the extra parameters to pass to the function
     * @return the result of applying the function to the given target
     */
    public R apply(T target, U parameters) {
        if (chosen == null) {
            synchronized (this) {
                if (chosen == null) {
                    chosen = test.apply(target, parameters) ? trueBranch : falseBranch;
                }
            }
        }
        return chosen.apply(target, parameters);
    }

    /**
     * Version without extra parameter(s) of {@link #apply(Object, Object)}
     */
    @SuppressWarnings("unchecked")
    public R apply(T target) {
        return apply(target, (U) new Object[0]);
    }

    /**
     * Creates a {@link SpigotFunctionChooser} that uses the success parameter when the action completes without an
     * exception and otherwise uses the failure parameter.
     * <p>
     * The action is, per the doc for {@link SpigotFunctionChooser} only called <em>once</em>.
     *
     * @param action  the action to invoke
     * @param success the branch to take when no exception occurs
     * @param failure the branch to take when an exception occurs
     * @param <T>     the type of the class containing the method
     * @param <U>     the type of the parameter(s)
     * @param <R>     the return type of the method
     * @return a {@link SpigotFunctionChooser} that picks the branch based on whether action threw an exception
     */
    private static <T, U, R> SpigotFunctionChooser<T, U, R> onException(ExceptionalFunction<T, U, R> action,
                                                                        BiFunction<T, U, R> success,
                                                                        BiFunction<T, U, R> failure) {
        return new SpigotFunctionChooser<>(
                (t, u) -> {
                    try {
                        action.apply(t, u);
                        return true;
                    } catch (ExceptionalFunction.WrappedException e) {
                        return false;
                    }
                },
                success, failure
        );
    }

    /**
     * Calls the Spigot API method if possible, otherwise uses reflection to access same method.
     * Useful for API methods that were only added after a certain version. Caches chosen method for performance.
     *
     * <p> Note: 1.16 is last version with Spigot-mapped fields, 1.17 with Spigot-mapped methods.
     *
     * @param apiCall A reference to the function that should be called
     * @param clazz   The class containing the function to be accessed via reflection
     * @param name    The name of the function to be accessed via reflection
     * @return A new instance of {@link SpigotFunctionChooser}
     */
    public static <T, U, R> SpigotFunctionChooser<T, U, R> apiCompatReflectionCall(ExceptionalFunction<T, U, R> apiCall,
                                                                                   Class<T> clazz, String name,
                                                                                   String... argTypes) {
        return onException(apiCall, apiCall, Reflector.memoiseMethodInvocation(clazz, name, argTypes));
    }

    /**
     * Calls the Spigot API method if possible, otherwise uses the provided function as a workaround.
     * <p>
     * This should be used to avoid reflection wherever possible, making the plugin more compatible.
     * Chosen method is cached for performance. Do not use method references as they are eagerly-bound in Java 8.
     * </p>
     *
     * @param apiCall A reference to the function that should be called
     * @param altFunc A function that should instead be called if API method not available.
     * @return A new instance of {@link SpigotFunctionChooser}
     */
    public static <T, U, R> SpigotFunctionChooser<T, U, R> apiCompatCall(ExceptionalFunction<T, U, R> apiCall, BiFunction<T, U, R> altFunc) {
        return onException(apiCall, apiCall, altFunc);
    }

    @FunctionalInterface
    public interface ExceptionalFunction<T, U, R> extends BiFunction<T, U, R> {
        // TODO might want to only check for NoSuchMethodException,
        //  cause what if the method is there and the exception is for some other reason?

        /**
         * Called by {@link #apply(Object, Object)}, this method is the target of the functional interface and where you can
         * write your logic, that might throw an exception.
         *
         * @param t the function argument
         * @return the function result
         */
        R applyWithException(T t, U params) throws Throwable;

        /**
         * {@inheritDoc}
         *
         * @param t {@inheritDoc}
         * @return {@inheritDoc}
         * @throws WrappedException if any *Throwable* is thrown
         */
        @Override
        default R apply(T t, U u) {
            try {
                return applyWithException(t, u);
            } catch (Throwable e) {
                throw new WrappedException(e);
            }
        }

        class WrappedException extends RuntimeException {
            WrappedException(Throwable cause) {
                super(cause);
            }
        }
    }
}
