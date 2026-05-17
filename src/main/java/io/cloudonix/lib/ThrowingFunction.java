package io.cloudonix.lib;

import java.util.function.Function;

/**
 * An functional interface to be implemented by functions used with {@link Futures#on(Class, ThrowingFunction)}.
 * 
 * This is just like {@link Function} exception it does not limit throwing exceptions
 * @author odeda
 *
 * @param <U> function input value type
 * @param <V> function output value type
 */
@FunctionalInterface
public interface ThrowingFunction<U,V> {
	V apply(U value) throws Throwable;
}
