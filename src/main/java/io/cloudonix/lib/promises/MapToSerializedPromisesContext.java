package io.cloudonix.lib.promises;

import java.util.function.Function;

import io.vertx.core.Future;

public class MapToSerializedPromisesContext<T,G> implements Function<T, io.vertx.core.Future<G>> {

	private Function<T, Future<G>> userMapper;
	private Future<G> lastPromise = Future.succeededFuture();

	public MapToSerializedPromisesContext(Function<T, Future<G>> mapper) {
		this.userMapper = mapper;
	}

	@Override
	public synchronized Future<G> apply(T t) {
		return lastPromise = lastPromise.compose(__ -> userMapper.apply(t));
	}

}
