package io.cloudonix.lib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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
	
	@FunctionalInterface
	public interface ThrowingFunction<U,V> {
		V apply(U value) throws Throwable;
	}

	public static <T, E extends Throwable> Function<Throwable, ? extends T> on(Class<E> errType,
			ThrowingFunction<E, ? extends T> fn) {
		return t -> {
			Throwable cause = t;
			while (Objects.nonNull(cause)) {
				if (errType.isInstance(cause))
					break;
				cause = cause.getCause();
			}
			if (Objects.isNull(cause))
				Thrower.spit(t);
			@SuppressWarnings("unchecked")
			E e = (E) cause;
			try {
				return fn.apply(e);
			} catch (Throwable e1) {
				Thrower.spit(e1);
				return null; // won't actually run, but java can't detect that spit() throws
			}
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
	 * 
	 * @param list
	 *            the list to operate on
	 * @param opporation
	 *            the operation to execute on every item of the list
	 * @return a CompletableFuture that will complete when all operations on all
	 *         items are finished processing.
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
	 * Convert a Vert.x async action that cannot fail (takes a {@link Handler}) to a
	 * Java CompletableFuture
	 * 
	 * @param action
	 *            Async action that takes a Vert.x {@link Handler} as a callback.
	 * @return a CompletableFuture that will complete successfully when the handler
	 *         is caller, and cannot complete exceptionally
	 */
	public static <T> CompletableFuture<T> fromHandler(Consumer<Handler<T>> action) {
		CompletableFuture<T> f = new CompletableFuture<>();
		action.accept(f::complete);
		return f;
	}

	/**
	 * Returns a new CompletableFuture that is completed when any of the given
	 * CompletableFutures complete, with the same result. Otherwise, if it completed
	 * exceptionally, the returned CompletableFuture also does so, with a
	 * CompletionException holding this exception as its cause. If no
	 * CompletableFutures are provided, returns an incomplete CompletableFuture.
	 *
	 * @param futures
	 *            a stream of CompletableFutures
	 * @return a new CompletableFuture that is completed with the result or
	 *         exception of any of the given CompletableFutures when one completes
	 * @throws NullPointerException
	 *             if the array or any of its elements are {@code null}
	 */
	public static <G> CompletableFuture<Object> anyOf(Stream<CompletableFuture<G>> futures) {
		return CompletableFuture.anyOf(futures.toArray(CompletableFuture[]::new));
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
		return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
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
	 * Returns a new CompletableFuture that is completed when the first future in the stream
	 * completes, with that future's completion value. If all of the futures in the stream completed
	 * exceptionally, then returned CompletableFuture is completed exceptionally with the exception
	 * with which the last future completed exceptionally (chronologically, not in order).
	 * 
	 * If no futures where provided in the stream (hence no future completed exceptionally or otherwise)
	 * the returned CompletableFuture completes exceptionally with the {@link NoSuchElementException} exception.
	 * 
	 * @param futures {@link Stream} of futures to consider
	 * @return A {@link CompletableFuture} that completes when the first future from the stream completes successfully 
	 */
	public static <G> CompletableFuture<G> resolveAny(Stream<CompletableFuture<G>> futures) {
		AtomicReference<Throwable> lastFailure = new AtomicReference<>();
		AtomicBoolean wasCompleted = new AtomicBoolean();
		CompletableFuture<G> res = new CompletableFuture<G>();
		Futures.<Void>allOf(futures.map(f -> f.thenAccept(v -> {
			if (wasCompleted.compareAndSet(false, true))
				res.complete(v);
			}).exceptionally(t -> {
				lastFailure.set(t);
				return null;
			}))).thenAccept(v -> {
				if (wasCompleted.get())
					return;
				if (Objects.nonNull(lastFailure.get()))
					res.completeExceptionally(new CompletionException(lastFailure.get()));
				else
					res.completeExceptionally(new CompletionException(new NoSuchElementException()));
			});
		return res;
	}
	
	/**
	 * Returns a new CompletableFuture that is completed when the first future in the list
	 * completes, with that future's completion value. If all of the futures in the list completed
	 * exceptionally, then returned CompletableFuture is completed exceptionally with the exception
	 * with which the last future completed exceptionally (chronologically, not in order).
	 * 
	 * If no futures where provided in the list (hence no future completed exceptionally or otherwise)
	 * the returned CompletableFuture completes exceptionally with the {@link NoSuchElementException} exception.
	 * 
	 * @param futures {@link List} of futures to consider
	 * @return A {@link CompletableFuture} that completes when the first future from the stream completes successfully 
	 */
	public static <G> CompletableFuture<G> resolveAny(List<CompletableFuture<G>> futures) {
		return resolveAny(futures.stream());
	}
	
	private static class Holder<T> {
		T value;
		public Holder(T val) {
			value = val;
		}
	}

	/**
	 * wait for all of the futures to complete and return a list of their results
	 * 
	 * @param futures
	 *            the stream to execute allOf on
	 * @return a CompletableFuture that will complete when all completableFutures in
	 *         the stream are completed and contains a list of their results
	 */
	public static <G> CompletableFuture<List<G>> resolveAll(Stream<CompletableFuture<G>> futures) {
		ConcurrentSkipListMap<Integer, Holder<G>> out = new ConcurrentSkipListMap<>();
		AtomicInteger i = new AtomicInteger(0);
		return allOf(futures.map(f -> {
			int index = i.getAndIncrement();
			return f.thenAccept(v -> out.put(index, new Holder<>(v)));
		}))
		.thenApply(v -> out.values().stream().map(h -> h.value).collect(Collectors.toList()));
	}

	/**
	 * wait for all of the futures to complete and return a list of their results
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

	/**
	 * Generate a CompletableFuture composition function that delays the return of an arbitrary value
	 * @param delay delay in milliseconds to impart on the value
	 * @return A function to be used in @{link {@link CompletableFuture#thenCompose(Function)}
	 */
	public static <T> Function<T, CompletableFuture<T>> delay(long delay) {
		CompletableFuture<T> future = new CompletableFuture<>();
		return value -> {
			new Timer(true).schedule(new TimerTask() {
				@Override
				public void run() {
					future.complete(value);
				}}, delay);
			return future;
		};
	}
}
