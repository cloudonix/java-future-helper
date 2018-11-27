package io.cloudonix.lib;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class StreamFutureResolver<T> implements Collector<CompletableFuture<T>, Collection<?>, Stream<T>> {
	
	ConcurrentLinkedDeque<AtomicReference<T>> buffer = new ConcurrentLinkedDeque<>();
	AtomicInteger count = new AtomicInteger(0);

	@Override
	public Supplier<Collection<?>> supplier() {
		return () -> buffer;
	}

	@Override
	public BiConsumer<Collection<?>, CompletableFuture<T>> accumulator() {
		return (deq, fut) -> {
			count.incrementAndGet();
			fut.thenAccept(this::add);
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
				AtomicReference<T> ref = buffer.pollFirst();
				count.decrementAndGet();
				return Objects.nonNull(ref) ? ref.get() : null;
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
		buffer.add(new AtomicReference<>(value));
	}
}
