package io.cloudonix.lib;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;

/**
 * Work in progress helper that is specific to Vert.x {@link Promise}s and {@link Future}s.
 * 
 * This is a collection of static methods that can help perform interesting workflows that consume
 * or create Vert.x Promises and Futures, and eventually this will be finalized in Future-Helper 4,
 * but for now this is just a playground.
 * 
 * @author odeda
 */
public class Promises {
	
	/**
	 * A helper to handle specific errors using {@link Future#otherwise(Function)}, in a style idiomatic with Java's
	 * {@code try...catch(E1)...catch(E2)} syntax.
	 * 
	 * Example usage:
	 * 
	 * <pre><code>
	 * createSomePromiseThatCanReject()
	 * .otherwise(Promises.recover(DataAccessException.class, dae -> { return valueInCaseOfDataError; }))
	 * .otherwise(Promises.recover(IOException.class, ioe -> { return valueInCaseOfIOError; }))
	 * .onSuccess(value -> {
	 *   // successfully recovered from DataAccessException or IOException with different recoveries
	 * })
	 * </code></pre>
	 * 
	 * Because the mapper is not actually a {@code java.util.function.Function}, it can also throw checked exceptions:
	 * 
	 * <pre><code>
	 * createSomePromiseThatCanReject()
	 * .otherwise(Promises.recover(DataAccessException.class, dae -> { throw new IOException(dae) }))
	 * </code></pre>
	 * 
	 * when the mapper throws, the {@link Future} returned from the {@code otherwise()} call will reject(fail) with the
	 * new thrown exception;
	 * 
	 * <strong>Note:</strong> this method is an alias to {@link Futures#on(Class, io.cloudonix.lib.Futures.ThrowingFunction)}
	 * and suffers from all of its deficiencies
	 * 
	 * @param <T> type of value that we will try to recover to from the error
	 * @param <E> type of exception that we will try to handle
	 * @param errType The class of the exception that we will try to recover from (its nice to explicitly declare the error while the value is implicit)
	 * @param mapper a recovery mapper that takes the exception and either returns a value or throws another exception (rethrowing is also OK)
	 * @return a {@link Function} that can be used as the handler for {@link Future#otherwise(Function)}
	 */
	public static <T,E extends Throwable> Function<Throwable,T> recover(Class<E> errType, Futures.ThrowingFunction<E,? extends T> mapper) {
		return Futures.on(errType, mapper);
	}
	
	/**
	 * Wait for all of the promises to complete and return a list of their results.
	 * @param <T> Value type of the promises result
	 * @param futures the list of promises to resolve
	 * @return a promise that when all promises in resolved correctly, will resolve to a list of all of the results.
	 *         If any promise rejected, the returned promise will reject with the error from the first such rejection.
	 */
	@SafeVarargs
	public static <T> Future<List<T>> resolveAll(Future<T>...futures) {
		return resolveAll(Stream.of(futures));
	}
	
	/**
	 * Wait for all of the promises to complete and return a list of their results.
	 * @param <T> Value type of the promises result
	 * @param futures the list of promises to resolve
	 * @return a promise that when all promises in resolved correctly, will resolve to a list of all of the results.
	 *         If any promise rejected, the returned promise will reject with the error from the first such rejection.
	 */
	public static <T> Future<List<T>> resolveAll(List<Future<T>> futures) {
		return futures.stream().collect(resolvingCollector());
	}
	
	/**
	 * Wait for all of the promises to complete and return a list of their results.
	 * @param <T> Value type of the promises result
	 * @param futures the list of promises to resolve
	 * @return a promise that when all promises in resolved correctly, will resolve to a list of all of the results.
	 *         If any promise rejected, the returned promise will reject with the error from the first such rejection.
	 */
	public static <T> Future<List<T>> resolveAll(Stream<Future<T>> futures) {
		return futures.collect(resolvingCollector());
	}

	/**
	 * A collector that can be used to collect a stream of promises and produce a promise that will resolve when all the
	 * promises in the stream have resolved. If any of the streams in the process reject, the promise returned from the
	 * collector will reject with the error from the first such rejection.
	 * @param <T> Value type of the results of promises in the stream
	 * @return a collector that collects a stream of promises into a promise of a stream.
	 */
	public static <T> Collector<Future<T>, Object, Future<List<T>>> resolvingCollector() {
		Promise<List<T>> output = Promise.promise();
		AtomicInteger startCounter = new AtomicInteger(0), endCounter = new AtomicInteger(0);
		AtomicReference<TreeMap<Integer, T>> streamFinished = new AtomicReference<>();
		return new Collector<Future<T>, Object, Future<List<T>>>() {

			void checkCompletion() {
				if (streamFinished.get() == null || endCounter.get() < startCounter.get())
					return;
				output.tryComplete(new ArrayList<>(streamFinished.get().values()));
			}
			
			@Override
			public Supplier<Object> supplier() {
				return TreeMap::new;
			}

			@Override
			public BiConsumer<Object, Future<T>> accumulator() {
				return (list, f) -> {
					int resolveIndex = startCounter.getAndIncrement();
					@SuppressWarnings("unchecked")
					TreeMap<Integer,T> accum = (TreeMap<Integer, T>) list;
					f.onSuccess(result -> accum.put(resolveIndex, result)).onFailure(output::tryFail)
						.onSuccess(__ -> {
							endCounter.incrementAndGet();
							checkCompletion();
						});
				};
			}

			@Override
			public BinaryOperator<Object> combiner() {
				return (a,b) -> {
					@SuppressWarnings("unchecked")
					TreeMap<Integer,T> accumA = (TreeMap<Integer, T>) a;
					@SuppressWarnings("unchecked")
					TreeMap<Integer,T> accumB = (TreeMap<Integer, T>) b;
					accumA.putAll(accumB);
					return accumA;
				};
			}

			@Override
			public Function<Object, Future<List<T>>> finisher() {
				return o -> {
					@SuppressWarnings("unchecked")
					TreeMap<Integer, T> map = (TreeMap<Integer, T>) o;
					streamFinished.set(map);
					checkCompletion();
					return output.future();
				};
			}

			@SuppressWarnings("serial")
			@Override
			public Set<Characteristics> characteristics() {
				return new TreeSet<Characteristics>() {{
					add(Characteristics.CONCURRENT);
					add(Characteristics.UNORDERED);
				}};
			}};
	}
	
	/**
	 * Create a {@link Future} that will resolve when all the provided promises resolve.
	 * This method is similar to the {@link #resolveAll(Future...)} and similar methods, but doesn't return the results
	 * and therefore doesn't care about the input types and the developer can mix promises with different resolution types.
	 * @param futures the list of promises to resolve
	 * @return a promise that when all promises provided as input resolve correctly, it will resolve.
	 *         If any promise rejected, the returned promise will reject with the error from the first such rejection.
	 */
	@SafeVarargs
	public static Future<Void> waitForAll(Future<?>...futures) {
		@SuppressWarnings("unchecked")
		Future<Object> fs[] = (Future[])futures;
		return Promises.resolveAll(fs).mapEmpty();
	}

	/**
	 * Returns a new {@link Future} that is completed when any promise in the stream
	 * resolves, with that first such resolved value. If all of the promises in the stream rejected,
	 * then the returned Future is failed with the exception with which the last promise rejected (chronologically, not in order).
	 *
	 * If no futures where provided in the stream (hence no future rejected or resolved)
	 * the returned Future is rejected with the {@link NoSuchElementException} exception.
	 *
	 * @param <G> Value type of the stream's promises
	 * @param futures {@link Stream} of futures to consider
	 * @return A {@link Future} that completes when the first future from the stream completes successfully
	 */
	public static <G> Future<G> resolveAny(Stream<Future<G>> futures) {
		AtomicReference<Throwable> lastFailure = new AtomicReference<>();
		AtomicBoolean wasCompleted = new AtomicBoolean();
		Promise<G> res = Promise.promise();
		Function<Future<G>,Future<Void>> mapper = f -> f.<Void>map(v -> {
			if (wasCompleted.compareAndSet(false, true))
				res.complete(v);
			return null;
		}).otherwise(t -> {
			lastFailure.set(t);
			return null;
		});
		resolveAll(futures.map(mapper)).onSuccess(v -> {
			if (wasCompleted.get())
				return;
			if (lastFailure.get() != null)
				res.tryFail(lastFailure.get());
			else
				res.tryFail(new NoSuchElementException());
		});
		return res.future();
	}

	/**
	 * An analogous implementation to {@link CompletableFuture#thenCombine(CompletionStage, BiFunction)} for Vert.x
	 * {@link Future} that when both input futures resolve, calls the bi-mapper with both results to create a
	 * {@link Future} that will be used to complete the returned Future.
	 * 
	 * If either input promises rejects with a failure, the first failure will be used to reject the returned future.
	 * If the mapper throws an exception, that exception will be used to reject the returned future.
	 * @param <T> Resolution type of the first promise
	 * @param <U> Resolution type of the second promise
	 * @param <G> Resolution type of the mapper's resulting promise
	 * @param a first promise to combine
	 * @param b second promise to combine
	 * @param mapper mapper used to combine the result
	 * @return A promise that will resolve to the resolution of the promise returned from the mapper, or reject if any
	 *   error occured.
	 */
	public static <T,U,G> Future<G> combine(Future<T> a, Future<U> b, BiFunction<T,U,Future<G>> mapper) {
		class CombinedTuple {
			public CombinedTuple(BiFunction<T, U, Future<G>> mapper) {
				output = mapInput.future().compose(v -> { 
					Future<G> res = mapper.apply(firstRes, secondRes);
					if (res == null)
						res = Future.succeededFuture();
					return res;
				});
			}
			volatile T firstRes;
			volatile boolean wasFirstRes = false;
			volatile U secondRes;
			volatile boolean wasSecondRes = false;
			Promise<Void> mapInput = Promise.promise();
			Future<G> output;
			private boolean handledError(AsyncResult<?> res) {
				if (res.succeeded())
					return false;
				mapInput.tryFail(res.cause());
				return true;
			}
			public void handleFirst(AsyncResult<T> res) {
				if (handledError(res)) return;
				firstRes = res.result();
				wasFirstRes = true;
				sendResult();
			}
			public void handleSecond(AsyncResult<U> res) {
				if (handledError(res)) return;
				secondRes = res.result();
				wasSecondRes = true;
				sendResult();
			}
			private synchronized void sendResult() {
				if (!wasFirstRes || !wasSecondRes)
					return;
				mapInput.complete();
			}
		};
		CombinedTuple results = new CombinedTuple(mapper);
		a.onComplete(results::handleFirst);
		b.onComplete(results::handleSecond);
		return results.output;
	}
	
	/**
	 * An analogous implementation to {@link CompletableFuture#applyToEither(CompletionStage, Function)} for Vert.x
	 * {@link Future} that when either input futures resolve, calls the mapper with the first result to resolve to create a
	 * {@link Future} that will be used to complete the returned Future.
	 * 
	 * If both input promises rejects with a failure, the first failure will be used to reject the returned future.
	 * If the mapper throws an exception, that exception will be used to reject the returned future.
	 * @param <T> Resolution type of the promises
	 * @param <G> Resolution type of the mapper's resulting promise
	 * @param a first promise to resolve
	 * @param b second promise to resolve
	 * @param mapper mapper used to combine the result
	 * @return A promise that will resolve to the resolution of the promise returned from the mapper, or reject if
	 *   errors occur.
	 */
	public static <T,G> Future<G> either(Future<T> a, Future<T> b, Function<T,Future<G>> mapper) {
		Promise<T> result = Promise.promise();
		AtomicReference<Throwable> firstError = new AtomicReference<>(null);
		Handler<Throwable> failureHandler = t -> {
			if (!firstError.compareAndSet(null, t)) // if we failed to update the error ref, it means we are the second one
				result.tryFail(firstError.get());
		};
		a.onSuccess(result::tryComplete).onFailure(failureHandler);
		b.onSuccess(result::tryComplete).onFailure(failureHandler);
		return result.future().compose(v -> {
			Future<G> res = mapper.apply(v);
			if (res == null)
				res = Future.succeededFuture();
			return res;
		});
	}
	
	/**
	 * Generate a Vert.x Future composition function that delays the return of an arbitrary value
	 * @param <T> Value type of the promise
	 * @param delay delay in milliseconds to impart on the value
	 * @return A function to be used in @{link {@link Future#compose(Function)}
	 */
	public static <T> Function<T, Future<T>> delay(long delay) {
		Promise<T> promise = Promise.promise();
		return value -> {
			Timers.schedule(() -> promise.complete(value), delay);
			return promise.future();
		};
	}

}
