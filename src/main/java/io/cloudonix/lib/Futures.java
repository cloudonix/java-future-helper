package io.cloudonix.lib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Set of static function to help work with Java's {@link CompletableFuture} and Vert.x async callbacks (@{link {@link AsyncResult} handlers)
 * @author odeda
 */
public class Futures {

	/**
	 * Configurable features for {@link Futures}
	 * @author odeda
	 */
	public static class Features {
		/**
		 * When propagating Vert.x async handlers to CompletableFuture, should Futures be able to report the original call trace
		 * when an exception happens? This causes a slight performance hit as the call site needs to take a stack trace snapshot before
		 * executing the call. Set the system property <code>io.cloudonix.lib.futures.async_callsite_snapshots</code> to "<code>true</code>" to enable.
		 */
		public static final boolean ENABLE_ASYNC_CALLSITE_SNAPSHOTS = Boolean.valueOf(System.getProperty(
				"io.cloudonix.lib.futures.async_callsite_snapshots", "false"));
	}
	
	/**
	 * Provides a future that was completed exceptionally with the provided error
	 *
	 * @param <T> Value type for the promise
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
	 * @param <T> Value type for the promise
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
	 * @param <T> Value type for the promise
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

		@SuppressWarnings("deprecation")
		public static void spit(Throwable t) {
			except = t;
			try {
				Thrower.class.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
			}
		}
	}

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

	/**
	 * "catch" style syntax for {@link CompletableFuture#exceptionally(Function)} - allows catching specific exceptions with
	 * simple chainable syntax. Also allows rethrowing or converting exceptions.
	 * 
	 * Usage:
	 * 
	 * <pre><code>
	 * CompletableFuture.&lt;String>supplyAsync(() -> ...) // or some other operations
	 * .exceptionally(Futures.on(SomeExpectedException.class, e -> "The expected exception"))
	 * .exceptionally(t -> { 
	 *   // handle other throwables 
	 * });
	 * </code></pre>
	 * 
	 * Or rethtrow:
	 *  
	 * <pre><code>
	 * CompletableFuture.&lt;String>supplyAsync(() -> ...) // or some other operations
	 * .exceptionally(Futures.on(SomeExpectedException.class, e -> { throw e; }))
	 * .exceptionally(t -> { 
	 *   // handle other throwables 
	 * });
	 * </code></pre>
	 * 
	 * Or convert to another exception:
	 *  
	 * <pre><code>
	 * CompletableFuture.&lt;String>supplyAsync(() -> ...) // or some other operations
	 * .exceptionally(Futures.on(SomeExpectedException.class, e -> { throw new OtherException(e); }))
	 * .exceptionally(t -> { 
	 *   // handle other throwables 
	 * });
	 * </code></pre>
	 * 
	 * Please note that any exception thrown will be caught by a downstream <code>exceptionally()</code> handler. A set of 
	 * <code>exceptionally(Futures.on(...))</code> is not like a set of catches for a single try, but more like a set of nested
	 * try...catch clauses.
	 * 
	 * @param <T> Result type to recover from the exception
	 * @param <E> Exception type to catch
	 * @param errType The class of the exception type to catch
	 * @param fn Handler function that will be called when the CompletableFuture completed exceptionally with a throwable
	 *   that either matches the specified exception type, or is a {@link RuntimeException} that wraps the specified exception type.
	 *   The handler function may return a value that matches the result type or throw an exception.
	 * @return a function that can be supplied to a {@link CompletableFuture#exceptionally(Function)} method and that will
	 * catch only the specified exception type, rethrowing all other exceptions automatically.
	 */
	public static <T, E extends Throwable> Function<Throwable, T> on(Class<E> errType,
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
	 * Convert a Vert.x-style async call (with callback) to a Java {@link CompletableFuture}.
	 *
	 * The action is expected to consume a callback of the required type and provide it as the callback
	 * (async handler) to the Vert.x-style call. The completion stage returned will be completed when the
	 * async handler is called with a result: successfully if the result was a success (with the provided result
	 * as the value) or exceptionally if the result was a failure (with the provided failure as the exception).
	 *
	 * Because of deficiencies in Java's generic type resolution, the call must be specialized manually. For example:
	 *
	 * <code>
	 * Futures.&lt;JsonArray&gt;fromAsync(h -&gt; api.getArray(h)) // ...
	 * </code>
	 *
	 * @param <T> Value type for the callback result
	 * @param action Implementation of the async callback wrapper
	 * @return A promise that resolves when the result is a success or rejects when the result is a failure
	 */
	public static <T> CompletableFuture<T> fromAsync(Consumer<Handler<AsyncResult<T>>> action) {
		Exception snapshot = Features.ENABLE_ASYNC_CALLSITE_SNAPSHOTS ? new Exception() : null;
		CompletableFuture<T> fut = new CompletableFuture<>();
		action.accept(res -> {
			CompletableFuture.runAsync(() -> {
				if (res.failed())
					fut.completeExceptionally(recodeThrowable(res.cause(), snapshot));
				else
					fut.complete(res.result());
			});
		});
		return fut;
	}

	private static Throwable recodeThrowable(Throwable cause, Exception stSnapshot) {
		if (Objects.isNull(stSnapshot))
			return cause;
		CompletionException recodedTrace = new CompletionException(cause);
		recodedTrace.setStackTrace(stSnapshot.getStackTrace());
		return recodedTrace;
	}

	/**
	 * Convert a Vert.x-style async call (with callback) to a Java {@link CompletableFuture}, possibly retrying
	 * a failed call.
	 *
	 * The action is expected to consume a callback of the required type and provide it as the callback
	 * (async handler) to the Vert.x-style call. The completion stage returned will be completed when the
	 * async handler is called with a result: successfully if the result was a success (with the provided result
	 * as the value) or exceptionally if the result was a failure (with the provided failure as the exception).
	 *
	 * Because of deficiencies in Java's generic type resolution, the call must be specialized manually. For example:
	 *
	 * <code>
	 * Futures.&lt;JsonArray&gt;retryAsyncIf(h -&gt; api.getArray(h), // ...
	 * </code>
	 *
	 * @param <T> Value type for the callback result
	 * @param action Implementation of the async callback wrapper
	 * @param predicate a test to check if we need to retry the call. The predicate should return <code>true</code>
	 * if the call should be retried.
	 * @param tries Maximum number of tries to execute.
	 * @return A promise that resolves when the result is a success or rejects when the result is a failure and
	 * either there were no more tries or the predicate did not request a retry. If multiple tries where performed,
	 * the resolution or rejection will be of the last try success or failure.
	 */
	public static <T> CompletableFuture<T> retryAsyncIf(Consumer<Handler<AsyncResult<T>>> action,
			Predicate<Throwable> predicate, int tries) {
		return fromAsync(action)
				.thenApply(CompletableFuture::completedFuture)
				.exceptionally(exp -> {
					if ((tries - 1) > 0 && predicate.test(exp))
						return retryAsyncIf(action, predicate, tries - 1);
					return failedFuture(exp);
				})
				.thenCompose(x -> x);
	}

	/**
	 * Executed an async operation on every item in a list, and return a
	 * CompletableFuture when all operations on all items are finished processing.
	 *
	 * @param <T> Value type for the list
	 * @param list the list to operate on
	 * @param operation the operation to execute on every item of the list
	 * @return a CompletableFuture that will complete when all operations on all items are finished processing.
	 */
	public static <T> CompletableFuture<Void> executeAllAsync(List<T> list,
			Function<T, CompletableFuture<Void>> operation) {
		List<CompletableFuture<Void>> fut = new ArrayList<>();
		for (T u : list) {
			fut.add(operation.apply(u));
		}
		return CompletableFuture.allOf(fut.toArray(new CompletableFuture[fut.size()]));
	}

	/**
	 * Execute an async operation on all elements of the list, returning a promise to the
	 * converted list
	 *
	 * @param <T> Source list value type
	 * @param <G> Target list value type
	 * @param list the list to operate on
	 * @param operation the operation to execute on every item of the list
	 * @return a promise that will complete when all operations on all items are finished processing.
	 */
	public static <T, G> CompletableFuture<List<G>> executeAllAsyncWithResults(List<T> list,
			Function<T, CompletableFuture<G>> operation) {
		List<G> listOfRes = new ArrayList<>(Collections.nCopies(list.size(), null));
		List<CompletableFuture<Void>> fut = new ArrayList<>(Collections.nCopies(list.size(), null));
		for (int i = 0; i < list.size(); i++) {
			int j = i;
			fut.set(i, operation.apply(list.get(i)).thenAccept(res -> listOfRes.set(j, res)));
		}
		return CompletableFuture.allOf(fut.toArray(new CompletableFuture[fut.size()])).thenApply(v -> listOfRes);
	}

	/**
	 * Convert a Vert.x async action that cannot fail (takes a {@link Handler}) to a
	 * Java CompletableFuture
	 *
	 * @param <T> Value type of the handler
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
	 * @param <G> Value type of the stream's promises
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
	 * @param <G> Value type of the stream's promises
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
	 * @param <G> Value type of the stream's promises
	 * @param list the list to execute <code>allOf</code> on
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
	 * @param <G> Value type of the stream's promises
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
	 * @param <G> Value type of the lists's promises
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
	 * @param <G> Value type of the stream's promises
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
	 * @param <G> Value type of the list's promises
	 * @param futures
	 *            list of CompletableFutures to wait for their completion
	 * @return a CompletableFuture that will complete when all completableFutures in
	 *         the list are completed and contains a list of their results
	 */
	public static <G> CompletableFuture<List<G>> resolveAll(List<CompletableFuture<G>> futures) {
		return resolveAll(futures.stream());
	}

	/**
	 * wait for all of the futures to complete and return a list of their results
	 *
	 * @param <G> Value type of the promise arguments
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
	 * Create a stream collector to help resolve a stream of promises for values to a promise to a list of values
	 * @param <G> The type of futures resolved by this stream
	 * @return a collector that collects a stream of futures to a future list
	 */
	public static <G> Collector<CompletableFuture<G>, Collection<CompletableFuture<G>>, CompletableFuture<List<G>>> resolvingCollector() {
		return new Collector<CompletableFuture<G>, Collection<CompletableFuture<G>>, CompletableFuture<List<G>>>(){
			@Override
			public Supplier<Collection<CompletableFuture<G>>> supplier() {
				return ConcurrentLinkedQueue<CompletableFuture<G>>::new;
			}

			@Override
			public BiConsumer<Collection<CompletableFuture<G>>, CompletableFuture<G>> accumulator() {
				return Collection::add;
			}

			@Override
			public BinaryOperator<Collection<CompletableFuture<G>>> combiner() {
				return (a,b) -> { a.addAll(b); return a; };
			}

			@Override
			public Function<Collection<CompletableFuture<G>>, CompletableFuture<List<G>>> finisher() {
				return q -> resolveAll(q.stream());
			}

			@Override
			public Set<Characteristics> characteristics() {
				return Collections.singleton(Characteristics.CONCURRENT);
			}

		};
	}
	
	/**
	 * Create a stream collector to help resolve a stream of promises for values to a stream of values.
	 * Beware: this API does not leave a lot of room for handling errors gracefully. If any of the promises in the input
	 * stream fail, it would cause the resulting stream to throw a {@link CompletionException} somewhere through the processing
	 * of the resulting stream - the exact timing is hard to predict due to how streams operate.
	 * @param <G> The type of futures resolved by this stream
	 * @return a collector that collects a stream of futures to a stream of values
	 */
	public static <G> Collector<CompletableFuture<G>, Collection<?>, Stream<G>> resolvingCollectorToStream() {
		return new StreamFutureResolver<>();
	}

	/**
	 * Generate a CompletableFuture composition function that delays the return of an arbitrary value
	 * @param <T> Value type of the promise
	 * @param delay delay in milliseconds to impart on the value
	 * @return A function to be used in @{link {@link CompletableFuture#thenCompose(Function)}
	 */
	public static <T> Function<T, CompletableFuture<T>> delay(long delay) {
		CompletableFuture<T> future = new CompletableFuture<>();
		return value -> {
			Timers.schedule(() -> future.complete(value), delay);
			return future;
		};
	}
	
	/**
	 * Run the promise producing operation for each of the source values, each after the previous operation has completed successfully
	 * @param <T> type of source values
	 * @param <G> expected type of async operation result
	 * @param source list of source values
	 * @param mapper mapping operation
	 * @return a promise that will resolve with the list of results, if no error has occurred, or reject with the first error if such occurred.
	 */
	public static <T,G> CompletableFuture<List<G>> resolveConsecutively(List<T> source, Function<T, CompletableFuture<G>> mapper) {
		return resolveConsecutively(source.stream(), mapper, true);
	}
	
	/**
	 * Run the promise producing operation for each of the source values, each after the previous operation has completed successfully - 
	 * or if <code>false</code> is provided as the last argument - after it has rejected
	 * @param <T> type of source values
	 * @param <G> expected type of async operation result
	 * @param source list of source values
	 * @param mapper mapping operation
	 * @param stopOnFailure whether to complete all operations even if one or more operations rejected
	 * @return a promise that will resolve with the list of results, if no error has occurred, or reject with the first error if such occurred.
	 */
	public static <T,G> CompletableFuture<List<G>> resolveConsecutively(List<T> source, Function<T, CompletableFuture<G>> mapper, boolean stopOnFailure) {
		return resolveConsecutively(source.stream(), mapper, stopOnFailure);
	}
	
	/**
	 * Run the promise producing operation for each of the source values, each after the previous operation has completed successfully
	 * @param <T> type of source values
	 * @param <G> expected type of async operation result
	 * @param source stream of source values
	 * @param mapper mapping operation
	 * @return a promise that will resolve with the list of results, if no error has occurred, or reject with the first error if such occurred.
	 */
	public static <T,G> CompletableFuture<List<G>> resolveConsecutively(Stream<T> source, Function<T, CompletableFuture<G>> mapper) {
		return resolveConsecutively(source, mapper, true);
	}

	/**
	 * Run the promise producing operation for each of the source values, each after the previous operation has completed successfully - 
	 * or if <code>false</code> is provided as the last argument - after it has rejected
	 * @param <T> type of source values
	 * @param <G> expected type of async operation result
	 * @param source stream of source values
	 * @param mapper mapping operation
	 * @param stopOnFailure whether to complete all operations even if one or more operations rejected
	 * @return a promise that will resolve with the list of results, if no error has occurred, or reject with the first error if such occurred.
	 */
	public static <T,G> CompletableFuture<List<G>> resolveConsecutively(Stream<T> source, Function<T, CompletableFuture<G>> mapper, boolean stopOnFailure) {
		ConcurrentLinkedQueue<G> buffer = new ConcurrentLinkedQueue<G>();
		return consecutively(source, mapper.andThen(f -> f.thenAccept(buffer::add)))
				.thenApply(v -> buffer.stream().collect(Collectors.toList()));
	}
	
	/**
	 * Run the promise producing operation for each of the source values, each after the previous operation has completed successfully
	 * @param <T> type of source values
	 * @param source list of source values
	 * @param operation mapping operation
	 * @return a promise that will resolve if no error has occurred, or reject with the first error if such occurred.
	 */
	public static <T> CompletableFuture<Void> consecutively(List<T> source, Function<T, CompletableFuture<Void>> operation) {
		return consecutively(source.stream(), operation, true);
	}
	
	/**
	 * Run the promise producing operation for each of the source values, each after the previous operation has completed successfully - 
	 * or if <code>false</code> is provided as the last argument - after it has rejected
	 * @param <T> type of source values
	 * @param source list of source values
	 * @param operation mapping operation
	 * @param stopOnFailure whether to complete all operations even if one or more operations rejected
	 * @return a promise that will resolve if no error has occurred, or reject with the first error if such occurred.
	 */
	public static <T> CompletableFuture<Void> consecutively(List<T> source, Function<T, CompletableFuture<Void>> operation, boolean stopOnFailure) {
		return consecutively(source.stream(), operation, stopOnFailure);
	}
	
	/**
	 * Run the promise producing operation for each of the source values, each after the previous operation has completed successfully
	 * @param <T> type of source values
	 * @param source stream of source values
	 * @param operation mapping operation
	 * @return a promise that will resolve if no error has occurred, or reject with the first error if such occurred.
	 */
	public static <T> CompletableFuture<Void> consecutively(Stream<T> source, Function<T, CompletableFuture<Void>> operation) {
		return consecutively(source, operation, true);
	}
	
	/**
	 * Run the promise producing operation for each of the source values, each after the previous operation has completed successfully - 
	 * or if <code>false</code> is provided as the last argument - after it has rejected
	 * @param <T> type of source values
	 * @param source stream of source values
	 * @param operation mapping operation
	 * @param stopOnFailure whether to complete all operations even if one or more operations rejected
	 * @return a promise that will resolve if no error has occurred, or reject with the first error if such occurred.
	 */
	public static <T> CompletableFuture<Void> consecutively(Stream<T> source, Function<T, CompletableFuture<Void>> operation, boolean stopOnFailure) {
		CompletableFuture<Void> next = CompletableFuture.completedFuture(null);
		AtomicReference<Throwable> firstError = new AtomicReference<>(null);
		Function <T,CompletableFuture<Void>> wrapper = stopOnFailure ? operation : 
			operation.andThen(v -> v.exceptionally(t -> { firstError.compareAndSet(null, t); return null; }));
		for (T val : new Iterable<T>() { public Iterator<T> iterator() { return source.iterator(); }}) {
			next = next.thenCompose(v -> wrapper.apply(val));
		}
		return next.thenCompose(v -> {
			if (firstError.get() == null)
				return CompletableFuture.completedFuture(null);
			// I wish they'd thought about failedFuture() for JDK 9
			CompletableFuture<Void> failedResult = new CompletableFuture<>();
			failedResult.completeExceptionally(firstError.get());
			return failedResult;
		});
	}

	/**
	 * This method can be used to forward the result (success or failure) of a CompletableFuture chain to a 
	 * Vert.x Future/Promise. This method returns a BiFunction for {@link CompletableFuture#handle(BiFunction)}.
	 * 
	 * After forwarding the chain is expected to terminate - the following completion stage, if set, will be run with
	 * a null value that only signals that the Vert.x future received the result, and have no meaning other than that.
	 * 
	 * Example:
	 * <code>
	 * stage.toCompletableFuture().handle(Futures.forward(vertxPromise::handle));
	 * </code>
	 * @param <T> The value type for the Vert.x handler
	 * @param destination The Vert.x handler - this should be Future/Promise::handle 
	 * @return a BiFunction that returns a Void null for use in a terminating CompletableFuture.handle()
	 */
	public static <T> BiFunction<T, Throwable, Void> forward(Consumer<AsyncResult<T>> destination) {
		return (v,t) -> { 
			destination.accept(t == null ? Future.succeededFuture(v) : Future.failedFuture(t));
			return null;
		};
	}
	
}
