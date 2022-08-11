package io.cloudonix.lib;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;

import java.io.IOException;
import java.util.HashMap;

import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.Promise;

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

}
