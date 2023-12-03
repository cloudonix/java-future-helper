package io.cloudonix.lib;

import java.util.concurrent.*;
import java.util.stream.Stream;

import org.junit.Test;

import static org.junit.Assert.*;

public class TestFuturesUnlessFailed {
	
	@Test
	public void testWaitForAlreadyCompleted() throws ExecutionException, InterruptedException {
		Futures.allOfUnlessFailed(Stream.of(1,2,3,4,5).map(i -> {
			CompletableFuture<Boolean> f = new CompletableFuture<>();
			f.complete(true);
			return f;
		})).whenComplete((v,t) -> {
			assertNull(t);
		}).get();
	}
	
	@Test
	public void testWaitForAllCompletedAsync() throws InterruptedException, ExecutionException {
		ExecutorService exec = Executors.newSingleThreadExecutor();
		Futures.allOfUnlessFailed(Stream.of(1,2,3,4,5).map(i -> {
			CompletableFuture<Boolean> f = new CompletableFuture<>();
			exec.execute(() -> {
				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
				}
				f.complete(true);
			});
			return f;
		})).whenComplete((v,t) -> {
			assertNull(t);
		}).get();
	}
	
	@Test
	public void testOneFailed() throws InterruptedException, ExecutionException {
		CountDownLatch lock = new CountDownLatch(1);
		CompletableFuture<Boolean> res = Futures.allOfUnlessFailed(Stream.of(1,2,3,4,5).map(i -> {
			CompletableFuture<Boolean> f = new CompletableFuture<>();
			if (i == 3)
				f.completeExceptionally(new Exception("failed"));
			return f;
		}))
		.thenApply(__ -> false)
		.exceptionally(t -> {
			assertNotNull(t);
			if (t instanceof CompletionException)
				t = t.getCause();
			assertEquals("failed", t.getMessage());
			return true;
		}).whenComplete((v,t) -> lock.countDown());
		assertTrue(lock.await(200, TimeUnit.MILLISECONDS));
		assertTrue(res.get());
	}
	
}
