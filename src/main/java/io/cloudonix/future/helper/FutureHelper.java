
package io.cloudonix.future.helper;

import java.util.concurrent.CompletableFuture;

import io.cloudonix.lib.Futures;

public class FutureHelper extends Futures {
	
	public static <T> CompletableFuture<T> completedSuccessfully(T value) {
		return completedFuture(value);
	}

	/**
	 * An alias to {@link #failedFuture(Throwable)} that simulates the
	 * {@link CompletableFuture#completeExceptionally(Throwable)} semantics.
	 * 
	 * @param th an {@link Throwable} representing the error that occured
	 * @return A completion stage that has already completed exceptionally
	 */
	public static <V> CompletableFuture<V> completedExceptionally(Throwable th) {
		return failedFuture(th);
	}

}
