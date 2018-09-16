package io.cloudonix.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.UnmappableCharacterException;

import org.junit.Test;

public class TestFutureExceptions {
	
	@Test
	public void testOnSkipping() {
		Futures.failedFuture(new UnmappableCharacterException(0))
		.exceptionally(Futures.on(IllegalStateException.class, e -> {
			fail("Wasn't expected an illegal state exception");
			return null;
		}))
		.thenAccept(v -> {
			fail("The accept should haev been skipped");
		})
		.exceptionally(t -> {
			assertEquals(UnmappableCharacterException.class, t.getClass());
			return null;
		});
	}
	
	@Test
	public void testOnCatching() {
		Futures.failedFuture(new UnmappableCharacterException(0))
		.exceptionally(Futures.on(UnmappableCharacterException.class, e -> {
			assertEquals(UnmappableCharacterException.class, e.getClass());
			return 1;
		}))
		.thenAccept(i -> {
			assertEquals(1, i);
		})
		.exceptionally(t -> {
			fail("Not expected a an error after catching");
			return null;
		});
	}
	
	@Test
	public void testOnCatchingParent() {
		Futures.failedFuture(new UnmappableCharacterException(0))
		.exceptionally(Futures.on(IOException.class, e -> {
			assertEquals(UnmappableCharacterException.class, e.getClass());
			return 7;
		}))
		.thenAccept(i -> {
			assertEquals(7, i);
		})
		.exceptionally(t -> {
			fail("Not expected a an error after catching");
			return null;
		});
	}
	
	@Test
	public void testOnCatchingMultiple() {
		Futures.failedFuture(new UnmappableCharacterException(0))
		.exceptionally(Futures.on(IllegalStateException.class, e -> {
			fail("Wasn't expected an illegal state exception");
			return 3;
		}))
		.exceptionally(Futures.on(IOException.class, e -> {
			assertEquals(UnmappableCharacterException.class, e.getClass());
			return 5;
		}))
		.thenAccept(i -> {
			assertEquals(5, i);
		})
		.exceptionally(t -> {
			fail("Not expected a an error after catching");
			return null;
		});
	}
	
	@Test
	public void testOnCatchingMultipleCascade() {
		Futures.failedFuture(new UnmappableCharacterException(0))
		.exceptionally(Futures.on(CharacterCodingException.class, e -> {
			return 9;
		}))
		.exceptionally(Futures.on(IOException.class, e -> {
			fail("Should capture on grandparent when parent should catch");
			return 2;
		}))
		.thenAccept(i -> {
			assertEquals(9, i);
		})
		.exceptionally(t -> {
			fail("Not expected a an error after catching");
			return null;
		});
	}
	
	public static class CheckedException extends Exception {
		private static final long serialVersionUID = 1L;
		String value;
		public CheckedException(String value) {
			this.value = value;
		}
		public String getValue() {
			return value;
		}
	}
	
	@Test
	public void testCatchAndRelease() {
		Futures.failedFuture(new CheckedException("guard"))
		.exceptionally(Futures.on(CheckedException.class, CheckedException::getValue))
		.whenComplete((t,i) -> {
			assertNull(t);
			assertEquals("guard", i);
		});
	}
}
