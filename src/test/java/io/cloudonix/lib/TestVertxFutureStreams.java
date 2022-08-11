package io.cloudonix.lib;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class TestVertxFutureStreams {
	
	@Test
	public void testFutureStreamToStream() {
		List<Integer> expected = IntStream.range(0, 10).mapToObj(i -> i).collect(Collectors.toList());
		AtomicReference<List<Integer>> computed = new AtomicReference<>();
		genStream(10).collect(Promises.resolvingCollector()).onSuccess(l -> {
			computed.set(l);
			synchronized (computed) {
				computed.notify();
			}
		});
		synchronized (computed) {
			while (computed.get() == null)
				try {
					computed.wait();
				} catch (InterruptedException e) {
				}
		}
		assertThat(computed.get()).isEqualTo(expected);
	}	
	
	static Random r = new Random();
	private Future<Integer> delayedResult(int value) {
		Promise<Integer>  res = Promise.promise();
		new Thread(() -> {
			try {
				Thread.sleep(r.nextInt(500));
			} catch (InterruptedException e) {}
			res.complete(value);
		}).start();
		return res.future();
	}
	
	private Stream<Future<Integer>> genStream(int size) {
		return IntStream.range(0, size).mapToObj(this::delayedResult);
	}
}
