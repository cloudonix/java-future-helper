package io.cloudonix.lib;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamFutureResolver<T> implements Collector<CompletableFuture<T>, Collection<?>, Stream<T>> {

	class CompletedFuture<G> implements Future<G> {
		private G value;
		private Throwable error;

		public CompletedFuture(G value) {
			this.value = value;
		}

		public CompletedFuture(Throwable error) {
			this.error = error;
		}

		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}

		@Override
		public boolean isCancelled() {
			return false;
		}

		@Override
		public boolean isDone() {
			return true;
		}

		@Override
		public G get() throws InterruptedException, ExecutionException {
			if (Objects.nonNull(error))
				throw new ExecutionException(error);
			return value;
		}

		@Override
		public G get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			return get();
		}
	}
	
	ConcurrentLinkedDeque<AtomicReference<Future<T>>> buffer = new ConcurrentLinkedDeque<>();
	AtomicInteger count = new AtomicInteger(0);

	@Override
	public Supplier<Collection<?>> supplier() {
		return () -> buffer;
	}

	@Override
	public BiConsumer<Collection<?>, CompletableFuture<T>> accumulator() {
		return (deq, fut) -> {
			count.incrementAndGet();
			fut.thenAccept(this::add).exceptionally(this::reject);
		};
	}

	@Override
	public BinaryOperator<Collection<?>> combiner() {
		return (a,b) -> buffer;
	}

	@Override
	public Function<Collection<?>, Stream<T>> finisher() {
		return this::stream;
	}
	
	private Stream<T> stream(Collection<?> col) {
		return StreamSupport.stream(spliterator(), true);
	}

	private Spliterator<T> spliterator() {
		return Spliterators.spliterator(new Iterator<T>() {
			@Override
			public boolean hasNext() {
				return count.get() > 0;
			}

			@Override
			public T next() {
				while (buffer.isEmpty() && count.get() > 0) {
					try {
						Thread.sleep(10);
					} catch (InterruptedException e) {
					}
				}
				AtomicReference<Future<T>> ref = buffer.pollFirst();
				count.decrementAndGet();
				try {
					return Objects.nonNull(ref) ? ref.get().get() : null;
				} catch (InterruptedException e) { // can't happen
					throw new CompletionException(e);
				} catch (ExecutionException e) {
					throw new CompletionException(e.getCause());
				}
			}
		}, count.get(), Spliterator.SIZED | Spliterator.IMMUTABLE | Spliterator.CONCURRENT);
	}

	@SuppressWarnings("serial")
	@Override
	public Set<Characteristics> characteristics() {
		return new TreeSet<Characteristics>() {{
			add(Characteristics.CONCURRENT);
			add(Characteristics.UNORDERED);
		}};
	}

	private void add(T value) {
		buffer.add(new AtomicReference<>(new CompletedFuture<T>(value)));
	}
	
	private Void reject(Throwable err) {
		buffer.add(new AtomicReference<>(new CompletedFuture<T>(err)));
		return null;
	}
}
