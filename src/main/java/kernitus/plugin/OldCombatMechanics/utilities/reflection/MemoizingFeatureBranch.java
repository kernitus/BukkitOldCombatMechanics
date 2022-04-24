/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package kernitus.plugin.OldCombatMechanics.utilities.reflection;

import java.util.function.Function;

/**
 * Chooses a function to apply based on a test supplier, remembers te choice and only uses the corresponding
 * function in the future.
 * <p>
 * The branch to pick is determined during the <em>first execution of its {@link #apply(Object)} method!</em>.
 * This means that no matter how often the feature branch is invoked, it will never reconsider its choice.
 *
 * @param <T> the type of the entity to apply the function to
 * @param <R> the return type of the function
 */
public class MemoizingFeatureBranch<T, R> {

    private final Function<T, Boolean> test;
    private final Function<T, R> trueBranch;
    private final Function<T, R> falseBranch;
    private Function<T, R> chosen;

    /**
     * Creates a new {@link MemoizingFeatureBranch}, which chooses between two given functions.
     *
     * @param test        the test supplier that will be invoked to choose a branch
     * @param trueBranch  the branch to pick when then test is true
     * @param falseBranch the branch to pick when then test is false
     */
    public MemoizingFeatureBranch(Function<T, Boolean> test, Function<T, R> trueBranch, Function<T, R> falseBranch){
        this.test = test;
        this.trueBranch = trueBranch;
        this.falseBranch = falseBranch;
    }

    /**
     * Applies the stored action to the given target and chooses what branch to use on the first call.
     *
     * @param target the target to apply it to
     * @return the result of applying the function to the given target
     */
    public R apply(T target){
        if(chosen == null){
            synchronized(this){
                if(chosen == null){
                    chosen = test.apply(target) ? trueBranch : falseBranch;
                }
            }
        }
        return chosen.apply(target);
    }

    /**
     * Creates a {@link MemoizingFeatureBranch} that uses the success parameter when the action completes without an
     * exception and otherwise uses the failure parameter.
     * <p>
     * The action is, per the doc for {@link MemoizingFeatureBranch} only called <em>once</em>.
     *
     * @param action  the action to invoke
     * @param success the branch to take when no exception occurs
     * @param failure the branch to take when an exception occurs
     * @param <T>     the type of the
     * @param <R>     the type of the
     * @return a {@link MemoizingFeatureBranch} that picks the branch based on whether action threw an exception
     */
    public static <T, R> MemoizingFeatureBranch<T, R> onException(ExceptionalFunction<T, R> action,
                                                                  Function<T, R> success,
                                                                  Function<T, R> failure){
        return new MemoizingFeatureBranch<>(
                (t) -> {
                    try{
                        action.apply(t);
                        return true;
                    } catch(ExceptionalFunction.WrappedException e){
                        return false;
                    }
                },
                success, failure
        );
    }

    @FunctionalInterface
    public interface ExceptionalFunction<T, R> extends Function<T, R> {

        /**
         * Called by {@link #apply(Object)}, this method is the target of the functional interface and where you can
         * write your logic, that might throw an exception.
         *
         * @param t the function argument
         * @return the function result
         */
        R applyWithException(T t) throws Throwable;

        /**
         * {@inheritDoc}
         *
         * @param t {@inheritDoc}
         * @return {@inheritDoc}
         * @throws WrappedException if any any *Throwable* is thrown
         */
        @Override
        default R apply(T t){
            try{
                return applyWithException(t);
            } catch(Throwable e){
                throw new WrappedException(e);
            }
        }

        class WrappedException extends RuntimeException {
            WrappedException(Throwable cause){
                super(cause);
            }
        }
    }
}
