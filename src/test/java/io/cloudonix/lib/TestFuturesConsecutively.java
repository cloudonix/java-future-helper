package io.cloudonix.lib;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.*;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestFuturesConsecutively {
	
	@Test
	public void testList() throws InterruptedException, ExecutionException {
		ArrayList<Integer> in = new ArrayList<>(Arrays.asList(1,2,3,4,5)), out = new ArrayList<Integer>();
		Futures.consecutively(in, i -> randomDelay(i, 5,1000).thenAccept(out::add))
		.get();
		assertEquals(in, out);
	}
	
	@Test
	public void testError() throws InterruptedException {
		try {
			Futures.consecutively(Arrays.asList(1,2,3,4), i -> CompletableFuture.runAsync(() -> {
				if (i > 2)
					throw new RuntimeException(i + " is too large");
			})).get();
			fail("Expected an exception");
		} catch (ExecutionException e) {
			assertEquals("3 is too large", e.getCause().getMessage());
		}
	}
	
	@Test
	public void testIgnoreError() throws InterruptedException {
		ArrayList<Integer> in = new ArrayList<>(Arrays.asList(1,2,3,4,5)), out = new ArrayList<Integer>();
		try {
			Futures.consecutively(in, i -> CompletableFuture.runAsync(() -> {
				out.add(i);
				if (i > 2)
					throw new RuntimeException(i + " is too large");
			}), false).get();
			fail("Expected an exception");
		} catch (ExecutionException e) {
			assertEquals("3 is too large", e.getCause().getMessage());
			assertEquals(in, out);
		}
	}
	
	private <T> CompletableFuture<T> randomDelay(T value, long min, long max) {
		long delay = Math.abs(new Random().nextLong() % Math.max(1,max - min)) + min;
		return CompletableFuture.supplyAsync(() -> value).thenCompose(Futures.delay(delay));
	}
}
