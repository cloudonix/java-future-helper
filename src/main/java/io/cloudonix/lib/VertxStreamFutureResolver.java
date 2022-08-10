package io.cloudonix.lib;

import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import io.vertx.core.Future;

public class VertxStreamFutureResolver<T> implements Collector<Future<T>, Collection<?>, Stream<T>> {
	
	ConcurrentLinkedDeque<AtomicReference<Future<T>>> buffer = new ConcurrentLinkedDeque<>();
	AtomicInteger count = new AtomicInteger(0);

	@Override
	public Supplier<Collection<?>> supplier() {
		return () -> buffer;
	}

	@Override
	public BiConsumer<Collection<?>, Future<T>> accumulator() {
		return (deq, fut) -> {
			count.incrementAndGet();
			fut.onSuccess(this::add).onFailure(this::reject);
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
				if (ref == null)
					return null;
				if (ref.get().failed())
					throw new CompletionException(ref.get().cause());
				return ref.get().result();
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
		buffer.add(new AtomicReference<>(Future.succeededFuture(value)));
	}
	
	private Void reject(Throwable err) {
		buffer.add(new AtomicReference<>(Future.failedFuture(err)));
		return null;
	}
}
