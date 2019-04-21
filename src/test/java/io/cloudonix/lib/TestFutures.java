package io.cloudonix.lib;

import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.Stream.Builder;

import org.junit.Test;

import io.vertx.core.Future;

import static org.junit.Assert.*;

public class TestFutures {
	
	@Test
	public void testAllOfStreamFailure() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		Futures.allOf(Stream.of(1,2,3,4,5).map(i -> {
			CompletableFuture<Boolean> f = new CompletableFuture<>();
			if (i == 3)
				f.complete(true);
			return f;
		})).whenComplete((v,t) -> {
			lock.countDown();
		});
		
		assertFalse(lock.await(200, TimeUnit.MILLISECONDS));
	}
	
	@Test
	public void testAllOfStreamSuccess() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		Futures.allOf(Stream.of(1,2,3,4,5).map(i -> {
			CompletableFuture<Boolean> f = new CompletableFuture<>();
			f.complete(true);
			return f;
		})).whenComplete((v,t) -> {
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testAllOfListFailure() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		Futures.allOf(Stream.of(1,2,3,4,5).map(i -> {
			CompletableFuture<Boolean> f = new CompletableFuture<>();
			if (i == 3)
				f.complete(true);
			return f;
		}).collect(Collectors.toList())).whenComplete((v,t) -> {
			assertNull(t);
			lock.countDown();
		});
		
		assertFalse(lock.await(200, TimeUnit.MILLISECONDS));
	}
	
	@Test
	public void testAllOfListSuccess() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		Futures.allOf(Stream.of(1,2,3,4,5).map(i -> {
			CompletableFuture<Boolean> f = new CompletableFuture<>();
			f.complete(true);
			return f;
		}).collect(Collectors.toList())).whenComplete((v,t) -> {
			assertNull(t);
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testAnyOf() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		Futures.anyOf(Stream.of(1,2,3,4,5).map(i -> {
			CompletableFuture<Boolean> f = new CompletableFuture<>();
			if (i == 3)
				f.complete(true);
			return f;
		})).whenComplete((v,t) -> {
			assertNull(t);
			assertTrue(v instanceof Boolean);
			assertTrue(((Boolean)v).booleanValue());
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testAnyOfWithException() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		Futures.anyOf(Stream.of(1,2,3,4,5).map(i -> {
			CompletableFuture<Boolean> f = new CompletableFuture<>();
			if (i == 3)
				f.completeExceptionally(new Exception());
			return f;
		})).whenComplete((v,t) -> {
			assertNotNull(t);
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testCompletedFutureVoid() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		CompletableFuture<Void> f = Futures.completedFuture();
		f.whenComplete((v,t) -> {
			assertNull(t);
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testCompletedFutureWithValue() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		CompletableFuture<Integer> f = Futures.completedFuture(42);
		f.whenComplete((v,t) -> {
			assertNull(t);
			assertNotNull(v);
			assertEquals(42, v.intValue());
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}

	@Test
	public void testExecuteAllAsyncSuccess() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		List<Integer> values = Arrays.asList(1,2,3,4,5);
		ExecutorService service = Executors.newSingleThreadExecutor();
		Futures.executeAllAsync(values, v -> {
			CompletableFuture<Void> f = new CompletableFuture<>();
			service.execute(() -> f.complete(null));
			return f;
		}).whenComplete((v,t) -> {
			assertNull(t);
			lock.countDown();
		});
		assertTrue(lock.await(2, TimeUnit.SECONDS));
		service.shutdown();
	}

	@Test
	public void testExecuteAllAsyncFailure() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		List<Integer> values = Arrays.asList(1,2,3,4,5);
		ExecutorService service = Executors.newSingleThreadExecutor();
		Futures.executeAllAsync(values, v -> {
			CompletableFuture<Void> f = new CompletableFuture<>();
			service.execute(() -> {
				if (v == 3)
					f.completeExceptionally(new Exception());
				else
					f.complete(null);
			});
			return f;
		}).whenComplete((v,t) -> {
			assertNotNull(t);
			lock.countDown();
		});
		assertTrue(lock.await(2, TimeUnit.SECONDS));
		service.shutdown();
	}
	
	@Test
	public void testExecuteAllAsyncWithResults() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		List<Integer> values = Arrays.asList(1,2,3,4,5);
		ExecutorService service = Executors.newSingleThreadExecutor();
		
		Futures.executeAllAsyncWithResults(values, v -> {
			CompletableFuture<Integer> f = new CompletableFuture<>();
			service.execute(() -> {
				f.complete(v);
			});
			return f;
		}).whenComplete((v,t) -> {
			assertNull(t);
			assertEquals(values, v);
			lock.countDown();
		});

		assertTrue(lock.await(2, TimeUnit.SECONDS));
		service.shutdown();
	}
	
	@Test
	public void testExecuteAllAsyncWithResultsFailure() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		List<Integer> values = Arrays.asList(1,2,3,4,5);
		ExecutorService service = Executors.newSingleThreadExecutor();
		
		Futures.executeAllAsyncWithResults(values, v -> {
			CompletableFuture<Integer> f = new CompletableFuture<>();
			service.execute(() -> {
				if (v == 3)
					f.completeExceptionally(new Exception());
				else
					f.complete(v);
			});
			return f;
		}).whenComplete((v,t) -> {
			assertNotNull(t);
			lock.countDown();
		});

		assertTrue(lock.await(2, TimeUnit.SECONDS));
		service.shutdown();
	}
	
	@Test
	public void testExecuteBlocking() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		
		Futures.executeBlocking(() -> {
			Thread.sleep(200);
			return 42;
		}).whenComplete((v,t) -> {
			assertNull(t);
			assertNotNull(v);
			assertEquals(42, v.intValue());
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testExecuteBlockingFailure() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		
		Futures.executeBlocking(() -> {
			throw new Exception();
		}).whenComplete((v,t) -> {
			assertNotNull(t);
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testFailedFuture() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		
		Futures.failedFuture(new Exception())
		.whenComplete((v,t) -> {
			assertNotNull(t);
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testFromAsync() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);

		ExecutorService service = Executors.newSingleThreadExecutor();
		Futures.<Integer>fromAsync(h -> {
			service.execute(() -> {
				h.handle(Future.succeededFuture(42));
			});
		})
		.whenComplete((v,t) -> {
			assertNull(t);
			assertNotNull(v);
			assertEquals(42, v.intValue());
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
		service.shutdown();
	}
	
	@Test
	public void testFromAsyncFailure() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);

		ExecutorService service = Executors.newSingleThreadExecutor();
		Futures.<Integer>fromAsync(h -> {
			service.execute(() -> {
				h.handle(Future.failedFuture("error"));
			});
		})
		.whenComplete((v,t) -> {
			assertNotNull(t);
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
		service.shutdown();
	}
	
	@Test
	public void testFromHandler() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);

		ExecutorService service = Executors.newSingleThreadExecutor();
		Futures.<Integer>fromHandler(h -> {
			service.execute(() -> {
				h.handle(42);
			});
		})
		.whenComplete((v,t) -> {
			assertNull(t);
			assertNotNull(v);
			assertEquals(42, v.intValue());
			lock.countDown();
		});
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
		service.shutdown();
	}
	
//	@Test
//	public void testExceptionallyOn(Class<E>, Function<E, ? extends T>) {}
//	
	@Test
	public void testResolveAll() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		
		CompletableFuture<Integer> f1 = new CompletableFuture<Integer>();
		CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
		Futures.resolveAll(f1, f2)
		.whenComplete((v,t) -> {
			assertNull(t);
			assertNotNull(v);
			assertEquals(Arrays.asList(42, 77), v);
			lock.countDown();
		});
		
		f1.complete(42);
		f2.complete(77);
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testResolveAllFailure() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		
		CompletableFuture<Integer> f1 = new CompletableFuture<Integer>();
		CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
		Futures.resolveAll(f1, f2)
		.whenComplete((v,t) -> {
			assertNotNull(t);
			lock.countDown();
		});
		
		f1.complete(42);
		f2.completeExceptionally(new Exception());
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testResolveAllList() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		
		CompletableFuture<Integer> f1 = new CompletableFuture<Integer>();
		CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
		Futures.resolveAll(Arrays.asList(f1,f2))
		.whenComplete((v,t) -> {
			assertNotNull(t);
			lock.countDown();
		});
		
		f1.complete(42);
		f2.completeExceptionally(new Exception());
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	@Test
	public void testResolveAllStream() throws InterruptedException {
		CountDownLatch lock = new CountDownLatch(1);
		
		CompletableFuture<Integer> f1 = new CompletableFuture<Integer>();
		CompletableFuture<Integer> f2 = new CompletableFuture<Integer>();
		Futures.resolveAll(Stream.of(f1,f2))
		.whenComplete((v,t) -> {
			assertNotNull(t);
			lock.countDown();
		});
		
		f1.complete(42);
		f2.completeExceptionally(new Exception());
		
		assertTrue(lock.await(2, TimeUnit.SECONDS));
	}
	
	private class DelayedCount {
		int index;
		long delay;
	}
	
	@Test
	public void testResolveAllStreamOrder() throws InterruptedException, ExecutionException {
		Builder<DelayedCount> builder = Stream.builder();
		for (int i = 0; i < 10; i++) {
			DelayedCount c = new DelayedCount();
			c.index = i;
			c.delay = 1000-(i * 100);
			builder.add(c);
		}
		
		Futures.resolveAll(builder.build().map(c-> Futures.completedFuture(c.index).thenCompose(Futures.delay(c.delay))))
		.whenComplete((v,t) -> {
			assertNull(t);
			assertEquals(10, v.size());
			for (int i = 0; i < v.size(); i++)
				assertEquals(i + "th item is out of order", i, v.get(i).intValue());
		})
		.get();
	}
}
