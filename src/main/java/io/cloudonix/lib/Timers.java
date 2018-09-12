package io.cloudonix.lib;

import java.time.*;

import io.vertx.core.Vertx;

public class Timers {

	/**
	 * Set an operation to happen daily at a certain time
	 * 
	 * @param vertx
	 *            a Vertx object
	 * @param operation
	 *            the operation to be executed
	 * @param timeOfDay
	 *            the time of day to execute the operation
	 */
	public static void setDailyOperation(Vertx vertx, Runnable operation, LocalTime timeOfDay) {
		setDailyOperation(vertx, operation, timeOfDay, ZoneOffset.UTC);
	}

	/**
	 * Set an operation to happen daily at a certain time
	 * 
	 * @param vertx
	 *            a Vertx object
	 * @param operation
	 *            the operation to be executed
	 * @param timeOfDay
	 *            the time of day to execute the operation
	 * @param timeZone
	 *            the time zone that the time of day is referenced to
	 */
	public static void setDailyOperation(Vertx vertx, Runnable operation, LocalTime timeOfDay, ZoneOffset timeZone) {
		vertx.setTimer(getMilsUntil(timeOfDay, timeZone), id1 -> {
			operation.run();
			vertx.setPeriodic(24 * 60 * 60 * 1000, id2 -> {
				operation.run();
			});
		});
	}
	
	/**
	 * Set an operation to happen daily at a certain time
	 * @param a Vertx object
	 * @param the operation to be executed
	 * @param the UNIX epoch time of when to perform the first execution, in milliseconds
	 * @param the amount of milliseconds between each execution
	 */
	public static void setPeriodicOperation(Vertx vertx, Runnable operation, Long firstTime, Long recurrenceEvery) {
		vertx.setTimer(firstTime - Instant.now().toEpochMilli(), id1 -> {
			operation.run();
			vertx.setPeriodic(recurrenceEvery, id2 -> {
				operation.run();
			});
		});
	}
	
	private static Long getMilsUntil(LocalTime timeOfDay, ZoneOffset timeZone) {
		Long time = LocalDateTime.now().toLocalDate().atTime(timeOfDay).toEpochSecond(timeZone);
		Long now = Instant.now().getEpochSecond();
		if (time > now)
			return (time - now) * 1000;
		return (time + 86400 - now) * 1000;
	}
	
}
