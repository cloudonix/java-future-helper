package io.cloudonix.lib;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.Test;

import io.vertx.core.Future;

public class MapToSerializedPromisesContextTest {

	private static final int ITERATIONS = 20;
	private SecureRandom rand = new SecureRandom();
	
	@Test
	public void testInOrder() throws InterruptedException {
		System.out.println("Starting serialized test");
		CountDownLatch lock = new CountDownLatch(1);
		AtomicReference<Integer[]> result = new AtomicReference<>();
		Stream.iterate(0, a -> a + 1).limit(ITERATIONS).map(Promises.mapToSerializedPromises(this::stringMaker)).collect(Promises.resolvingCollector())
		.map(l -> l.stream().map(String::length).toArray(Integer[]::new))
		.onSuccess(result::set)
		.onComplete(__ -> lock.countDown());
		lock.await(100, TimeUnit.SECONDS);
		Integer[] resA = result.get();
		Integer[] resB = new Integer[resA.length];
		System.arraycopy(resA, 0, resB, 0, resA.length);
		Arrays.sort(resB);
		assertThat(Arrays.asList(resA), containsInRelativeOrder(resB));
	}
	
	private Future<String> stringMaker(int length) {
		return Future.succeededFuture().compose(Promises.delay(rand.nextInt(1000)))
					.onSuccess(__ -> System.out.println("Computing " + length))
					.map(__ -> String.join("", Collections.nCopies(length, "x")));
	}

}
