package io.cloudonix.lib;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;

public class Futures {

	/**
	 * Provides a future that was completed exceptionally with the provided error
	 * 
	 * @param error
	 *            the error to fail the future with
	 * @return a future that was completed exceptionally with the provided error
	 */
	public static <T> CompletableFuture<T> failedFuture(Throwable error) {
		CompletableFuture<T> fut = new CompletableFuture<T>();
		fut.completeExceptionally(error);
		return fut;
	}

	/**
	 * Provides a future that was completed with null
	 * 
	 * @return a future that was completed with null
	 */
	public static CompletableFuture<Void> completedFuture() {
		CompletableFuture<Void> fut = new CompletableFuture<Void>();
		fut.complete(null);
		return fut;
	}

	/**
	 * Provides a future that was completed successfully with the provided value
	 * 
	 * @param value
	 *            the value to complete the future with
	 * @return a future that was completed successfully with the provided value
	 */
	public static <T> CompletableFuture<T> completedFuture(T value) {
		CompletableFuture<T> f = new CompletableFuture<T>();
		f.complete(value);
		return f;
	}

	/**
	 * Provides a future that will be completed when a handler will be executed
	 * 
	 * @param action
	 *            an action to perform that receives a handler to execute
	 * @return a future that will be completed when a handler will be executed
	 */
	public static <T> CompletableFuture<T> successfulFuture(Consumer<Handler<T>> action) {
		CompletableFuture<T> f = new CompletableFuture<T>();
		action.accept(value -> f.complete(value));
		return f;
	}

	/**
	 * Executes a supplier in an asynchronous manner. The supplier can throw
	 * exceptions
	 * 
	 * @param sup
	 *            the supplier to execute
	 * @return a CompletableFuture that will be completed after the supplier
	 *         finished processing
	 */
	public static <T> CompletableFuture<T> executeBlocking(ThrowingSupplier<T> sup) {
		return completedFuture().<T>thenApplyAsync(v -> {
			try {
				return sup.get();
			} catch (Throwable t) {
				Thrower.spit(t);
				return null;
			}
		}).toCompletableFuture();
	}

	@FunctionalInterface
	public static interface ThrowingSupplier<T> {
		T get() throws Throwable;
	}

	static class Thrower {
		static Throwable except;

		Thrower() throws Throwable {
			throw except;
		}

		public static void spit(Throwable t) {
			except = t;
			try {
				Thrower.class.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
			}
		}
	}

	public static <T, E extends Throwable> Function<Throwable, ? extends T> on(Class<E> errType,
			Function<E, ? extends T> fn) {
		return t -> {
			if (!errType.isInstance(t))
				Thrower.spit(t);
			@SuppressWarnings("unchecked")
			E e = (E) t;
			return fn.apply(e);
		};
	}

	/**
	 * Executes a consumer that receives a handler as an argument, and instead of
	 * executing that handler it will return a CompletableFuture after it finishes
	 * processing. the handler is stricted to handle AsyncResult.
	 * 
	 * @param action
	 *            the consumer to execute
	 * @return a CompletableFuture that will be completed after the consumer
	 *         finished processing
	 */
	public static <T> CompletableFuture<T> fromAsync(Consumer<Handler<AsyncResult<T>>> action) {
		CompletableFuture<T> fut = new CompletableFuture<>();
		action.accept(res -> {
			if (res.failed())
				fut.completeExceptionally(res.cause());
			else
				fut.complete(res.result());
		});
		return fut;
	}

	/**
	 * The same as fromAsync only the handler isn't stricted to handle any specific
	 * object.
	 * 
	 * @param action
	 *            the consumer to execute
	 * @return a CompletableFuture that will be completed after the consumer
	 *         finished processing
	 */
	public static <T> CompletableFuture<T> fromAsyncSimple(Consumer<Handler<T>> action) {
		CompletableFuture<T> fut = new CompletableFuture<>();
		action.accept(lon -> {
			fut.complete(lon);
		});
		return fut;
	}

	/**
	 * Executed an async operation on every item in a list, and return a
	 * CompletableFuture when all operations on all items are finished processing.
	 * 
	 * @param list
	 *            the list to operate on
	 * @param opporation
	 *            the operation to execute on every item of the list
	 * @return a CompletableFuture that will complete when all operations on all
	 *         items are finished processing.
	 */
	public static <T> CompletableFuture<Void> executeAllAsync(List<T> list,
			Function<T, CompletableFuture<Void>> opporation) {
		List<CompletableFuture<Void>> fut = new ArrayList<>();
		for (T u : list) {
			fut.add(opporation.apply(u));
		}
		return CompletableFuture.allOf(fut.toArray(new CompletableFuture[fut.size()]));
	}
	
	/**
	 * list executeAllAsync only it completes with a list of the 
	 * @param list the list to operate on
	 * @param opporation the operation to execute on every item of the list
	 * @return a CompletableFuture that will complete when all operations on all items are finished processing.
	 */
	public static <T, G> CompletableFuture<List<G>> executeAllAsyncWithResults(List<T> list,
			Function<T, CompletableFuture<G>> opporation) {
		List<G> listOfRes = new ArrayList<>();
		List<CompletableFuture<Void>> fut = new ArrayList<>();
		for (T u : list) {
			fut.add(opporation.apply(u).thenAccept(res -> listOfRes.add(res)));
		}
		return CompletableFuture.allOf(fut.toArray(new CompletableFuture[fut.size()])).thenApply(v -> listOfRes);
	}

	/**
	 * Convert a Vert.x async action that cannot fail (takes a {@link Handler}) to a Java CompletableFuture
	 * @param action Async action that takes a Vert.x {@link Handler} as a callback.
	 * @return a CompletableFuture that will complete successfully when the handler is caller, and cannot complete exceptionally
	 */
	public static <T> CompletableFuture<T> fromHandler(Consumer<Handler<T>> action) {
		CompletableFuture<T> f = new CompletableFuture<>();
		action.accept(f::complete);
		return f;
	}

    /**
     * Returns a new CompletableFuture that is completed when any of
     * the given CompletableFutures complete, with the same result.
     * Otherwise, if it completed exceptionally, the returned
     * CompletableFuture also does so, with a CompletionException
     * holding this exception as its cause.  If no CompletableFutures
     * are provided, returns an incomplete CompletableFuture.
     *
     * @param futures a stream of CompletableFutures
     * @return a new CompletableFuture that is completed with the
     * result or exception of any of the given CompletableFutures when
     * one completes
     * @throws NullPointerException if the array or any of its elements are
     * {@code null}
     */
	public static <G> CompletableFuture<Object> anyOf(Stream<CompletableFuture<G>> futures) {
		return CompletableFuture.anyOf(futures.toArray(i -> new CompletableFuture[i]));
	}

	/**
	 * Executes CompletableFuture's allOf on a stream instead of an array
	 * 
	 * @param futures
	 *            the stream to execute allOf on
	 * @return a CompletableFuture that will complete when all completableFutures in
	 *         the stream are completed
	 */
	public static <G> CompletableFuture<Void> allOf(Stream<CompletableFuture<G>> futures) {
		return CompletableFuture.allOf(futures.toArray(i -> new CompletableFuture[i]));
	}

	/**
	 * Executes CompletableFuture's allOf on a list instead of an array
	 * 
	 * @param futures
	 *            the stream to execute allOf on
	 * @return a CompletableFuture that will complete when all completableFutures in
	 *         the list are completed
	 */
	public static <G> CompletableFuture<Void> allOf(List<CompletableFuture<G>> list) {
		return CompletableFuture.allOf(list.toArray(new CompletableFuture[list.size()]));
	}

	/**
	 * wait for all of the futueres to complete and return a list of their results
	 * 
	 * @param futures
	 *            the stream to execute allOf on
	 * @return a CompletableFuture that will complete when all completableFutures in
	 *         the stream are completed and contains a list of their results
	 */
	public static <G> CompletableFuture<List<G>> resolveAll(Stream<CompletableFuture<G>> futures) {
		List<G> out = Collections.synchronizedList(new LinkedList<>());
		return allOf(futures.map(f -> f.thenAccept(v -> out.add(v))))
				.thenApply(v -> out.stream().collect(Collectors.toList()));
	}

	/**
	 * wait for all of the futueres to complete and return a list of their results
	 * 
	 * @param futures
	 *            list of CompletableFutures to wait for their completion
	 * @return a CompletableFuture that will complete when all completableFutures in
	 *         the list are completed and contains a list of their results
	 */
	public static <G> CompletableFuture<List<G>> resolveAll(List<CompletableFuture<G>> futures) {
		return resolveAll(futures.stream());
	}

	/**
	 * wait for all of the futueres to complete and return a list of their results
	 * 
	 * @param futures
	 *            the array of CompletableFutures to wait for their completion
	 * @return a CompletableFuture that will complete when all completableFutures in
	 *         the array are completed and contains a list of their results
	 */
	@SafeVarargs
	public static <G> CompletableFuture<List<G>> resolveAll(CompletableFuture<G>... futures) {
		return resolveAll(Arrays.stream(futures));
	}

}