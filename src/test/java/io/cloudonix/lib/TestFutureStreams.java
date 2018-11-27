package io.cloudonix.lib;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.Test;

import static org.assertj.core.api.Assertions.*;

public class TestFutureStreams {

	@Test
	public void testFutureStreamToStream() {
		List<Integer> expected = IntStream.range(0, 10).mapToObj(i -> i).collect(Collectors.toList());
		List<Integer> computed = genStream(10).collect(new StreamFutureResolver<>()).sorted().collect(Collectors.toList());
		assertThat(computed).isEqualTo(expected);
	}	
	
	static Random r = new Random();
	private class DelayedCounted extends CompletableFuture<Integer> {
		private DelayedCounted(int index) {
			new Thread(() -> {
				try {
					Thread.sleep(r.nextInt(500));
				} catch (InterruptedException e) {}
				complete(index);
			}).start();
		}
	}
	
	private Stream<CompletableFuture<Integer>> genStream(int size) {
		return IntStream.range(0, size).mapToObj(DelayedCounted::new);
	}
}
