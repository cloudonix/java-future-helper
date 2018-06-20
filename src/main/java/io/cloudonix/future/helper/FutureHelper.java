package io.cloudonix.future.helper;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class FutureHelper {
	
	public static <T> CompletableFuture<T> failedFuture(Throwable error) {
		CompletableFuture<T> fut = new CompletableFuture<T>();
		fut.completeExceptionally(error);
		return fut;
	}

	public static CompletableFuture<Void> completedFuture() {
		CompletableFuture<Void> fut = new CompletableFuture<Void>();
		fut.complete(null);
		return fut;
	}
	
	public static <T> CompletableFuture<T> completedSuccessfully(T value) {
		CompletableFuture<T> f = new CompletableFuture<T>();
		f.complete(value);
		return f;
	}
	
	public static <T> CompletableFuture<T> successfulFuture(Consumer<Handler<T>> action) {
		CompletableFuture<T> f = new CompletableFuture<T>();
		action.accept(value -> f.complete(value));
		return f;
	}
	
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
	public static interface ThrowingSupplier <T> {
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
	
	public static <T,E extends Throwable> Function<Throwable, ? extends T> on(Class<E> errType, Function<E, ? extends T> fn) {
		return t -> {
			if (!errType.isInstance(t))
				Thrower.spit(t);
			@SuppressWarnings("unchecked") E e = (E)t;
			return fn.apply(e);
		};
	}

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
	
	public static <T> CompletableFuture<T> fromAsyncSimple(Consumer<Handler<T>> action) {
		CompletableFuture<T> fut = new CompletableFuture<>();
		action.accept(lon -> {
			fut.complete(lon);
		});
		return fut;
	}
	
	public static <T> CompletableFuture<Void> executeAllAsync(List<T> list, Function<T, CompletableFuture<Void>> opporation) {
		List<CompletableFuture<Void>> fut = new ArrayList<>();
		for(T u : list) {
			fut.add(opporation.apply(u));
		}
		CompletableFuture[] futArr = fut.toArray(new CompletableFuture[fut.size()]);
		return CompletableFuture.allOf(futArr);
	}
	
	public static void setDailyOperation(Vertx vertx, Runnable operation, LocalTime timeOfDay) {
		setDailyOperation(vertx, operation, timeOfDay, ZoneOffset.UTC);
	}
	
	public static void setDailyOperation(Vertx vertx, Runnable operation, LocalTime timeOfDay, ZoneOffset timeZone) {
		vertx.setTimer(getMilsUntil(timeOfDay, timeZone), id1 -> {
			operation.run();
			vertx.setPeriodic(24 * 60 * 60 * 1000, id2 -> {
				operation.run();
			});
		});
	}
	
	public static Long getMilsUntil(LocalTime timeOfDay, ZoneOffset timeZone) {
		Long time = LocalDateTime.now().toLocalDate().atTime(timeOfDay).toEpochSecond(timeZone);
		Long now = Instant.now().getEpochSecond();
		if (time > now)
			return (time - now) * 1000;
		return (time + 86400 - now) * 1000;
	}
	
	public static <T> CompletableFuture<T> fromHandler(Consumer<Handler<T>> action) {
		CompletableFuture<T> f = new CompletableFuture<>();
		action.accept(f::complete);
		return f;
	}
	
	public static <T> CompletableFuture<T> successfulFuture(T value) {
		CompletableFuture<T> f = new CompletableFuture<T>();
		f.complete(value);
		return f;
	}
	
}
