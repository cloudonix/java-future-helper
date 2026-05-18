package io.cloudonix.lib;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

public class TestPromiseResolvers {

	@SuppressWarnings("serial")
	@Test
	public synchronized void testWait() throws Throwable {
		Future<Integer> intres = Future.succeededFuture(5);
		Future<String> stres = Future.succeededFuture("Hello World");
		Future<HashMap<String,String>> mapres = Future.succeededFuture(new HashMap<String,String>() {{ put("foo", "bar"); }});
		Promise<Void> output = Promise.promise();
		Promises.waitForAll(intres, stres, mapres).onComplete(res -> {
			if (res.succeeded())
				output.complete();
			else
				output.fail(res.cause());
		});
		Future<Void> fut = output.future();
		while (!fut.isComplete())
			try {
				wait();
			} catch (InterruptedException e) {
			}
		if (fut.failed())
			throw fut.cause();
	}
	
	@Test
	public synchronized void testCombine() throws Throwable {
		Promise<Integer> first = Promise.promise();
		Promise<String> second = Promise.promise();
		Promise<Void> testDone = Promise.promise();
		Promises.combine(first.future(), second.future(), (i,s) -> {
			return Future.succeededFuture();
		}).onSuccess(__ -> testDone.complete()).onFailure(testDone::fail).onComplete(r -> {
			synchronized (this) {
				notify();
			}
		});
		new Thread(() -> {
			try {
				Thread.sleep((long) Math.floor(Math.random()*1000));
			} catch (InterruptedException e) {
			}
			first.complete(5);
		}).start();
		new Thread(() -> {
			try {
				Thread.sleep((long) Math.floor(Math.random()*1000));
			} catch (InterruptedException e) {
			}
			second.complete("Hello world");
		}).start();
		Future<Void> f = testDone.future();
		while (!f.isComplete()) {
			try {
				wait();
			} catch (InterruptedException e) {}
		}
		if (f.failed())
			throw f.cause();
	}

	@Test
	public synchronized void testCombineWithError() throws Throwable {
		Promise<Integer> first = Promise.promise();
		Promise<String> second = Promise.promise();
		Promise<Void> testDone = Promise.promise();
		Promises.combine(first.future(), second.future(), (i,s) -> {
			return Future.succeededFuture();
		}).onSuccess(__ -> testDone.complete()).onFailure(testDone::fail).onComplete(r -> {
			synchronized (this) {
				notify();
			}
		});
		new Thread(() -> {
			try {
				Thread.sleep((long) Math.floor(Math.random()*1000)+1200);
			} catch (InterruptedException e) {
			}
			first.fail(new IOException());
		}).start();
		new Thread(() -> {
			try {
				Thread.sleep((long) Math.floor(Math.random()*1000));
			} catch (InterruptedException e) {
			}
			second.fail(new RuntimeException());
		}).start();
		Future<Void> f = testDone.future();
		while (!f.isComplete()) {
			try {
				wait();
			} catch (InterruptedException e) {}
		}
		assertThat(f.failed(), is(equalTo(true)));
		assertThat(f.cause(), is(instanceOf(RuntimeException.class)));
	}

	@Test
	public synchronized void testComposeJoin() throws Throwable {
		Future<?> result = Future.all(Arrays.asList(
				Future.succeededFuture(1000L),
				Future.succeededFuture("Hello"),
				Future.succeededFuture(new JsonObject().put("foo", "bar")),
				Future.succeededFuture(Arrays.asList(1,2,3)),
				Future.succeededFuture(true)
				))
		.compose(Promises.combine((Long l, String s, JsonObject j, List<Integer> a, Boolean b) -> {
			assertThat(l, is(equalTo(1000L)));
			assertThat(s, is(equalTo("Hello")));
			assertThat(j.getString("foo"), is(equalTo("bar")));
			assertThat(a.size(), is(equalTo(3)));
			assertThat(b, is(equalTo(true)));
			return Future.succeededFuture();
		}))
		.onComplete(__ -> notify());
		while (!result.isComplete()) {
			try {
				wait();
			} catch (InterruptedException e) {}
		}
		if (result.failed())
			throw result.cause();
	}

	@Test
	public synchronized void testMapJoin() throws Throwable {
		AtomicInteger res = new AtomicInteger();
		Future<?> result = Future.all(Arrays.asList(
				Future.succeededFuture(1000L),
				Future.succeededFuture("Hello"),
				Future.succeededFuture(new JsonObject().put("foo", "bar")),
				Future.succeededFuture(Arrays.asList(1,2,3)),
				Future.succeededFuture(true)
				))
		.map(Promises.map((Long l, String s, JsonObject j, List<Integer> a, Boolean b) -> {
			assertThat(l, is(equalTo(1000L)));
			assertThat(s, is(equalTo("Hello")));
			assertThat(j.getString("foo"), is(equalTo("bar")));
			assertThat(a.size(), is(equalTo(3)));
			assertThat(b, is(equalTo(true)));
			return 5;
		}))
		.onSuccess(res::set)
		.onComplete(__ -> notify());
		while (!result.isComplete()) {
			try {
				wait();
			} catch (InterruptedException e) {}
		}
		if (result.failed())
			throw result.cause();
		assertThat(res.get(), is(equalTo(5)));
	}
}
