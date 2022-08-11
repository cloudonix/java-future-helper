package io.cloudonix.lib;

import java.util.HashMap;

import org.junit.Test;

import io.vertx.core.Future;
import io.vertx.core.Promise;

public class TestPromiseResolvers {

	@SuppressWarnings("serial")
	@Test
	public synchronized void typeLessResolver() throws Throwable {
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

}
