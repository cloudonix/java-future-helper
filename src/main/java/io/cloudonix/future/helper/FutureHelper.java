package io.cloudonix.future.helper;

import java.util.concurrent.CompletableFuture;

import io.cloudonix.lib.Futures;

public class FutureHelper extends Futures {
	
	public static <T> CompletableFuture<T> completedSuccessfully(T value) {
		return completedFuture(value);
	}
}
