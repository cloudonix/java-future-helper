package io.cloudonix.lib;

import java.time.*;
import java.util.concurrent.TimeUnit;

import io.vertx.core.Vertx;

public class Timers {

	/**
	 * Set an operation to happen daily at midnight UTC
	 * 
	 * @param vertx a Vertx instance where the periodic operation will be created
	 * @param operation the operation to be executed
	 * @param timeOfDay the time of day to execute the operation
	 */
	public static void setDailyOperation(Vertx vertx, Runnable operation) {
		setDailyOperation(vertx, operation, LocalTime.MIDNIGHT, ZoneOffset.UTC);
	}

	/**
	 * Set an operation to happen daily at a certain time in UTC
	 * 
	 * @param vertx a Vertx instance where the periodic operation will be created
	 * @param operation the operation to be executed
	 * @param timeOfDay the time of day to execute the operation
	 */
	public static void setDailyOperation(Vertx vertx, Runnable operation, LocalTime timeOfDay) {
		setDailyOperation(vertx, operation, timeOfDay, ZoneOffset.UTC);
	}

	/**
	 * Set an operation to happen daily at a certain time
	 * 
	 * @param vertx a Vertx instance where the periodic operation will be created
	 * @param operation the operation to be executed
	 * @param timeOfDay the time of day to execute the operation
	 * @param timeZone the time zone that the time of day is referenced to
	 */
	public static void setDailyOperation(Vertx vertx, Runnable operation, LocalTime timeOfDay, ZoneOffset timeZone) {
		setPeriodicOperation(vertx, operation, getMilsForNext(timeOfDay, timeZone), TimeUnit.DAYS.toMillis(1));
	}
	
	/**
	 * Set an operation to happen daily at a certain time
	 * @param a Vertx instance where the periodic operation will be created
	 * @param the operation to be executed
	 * @param the UNIX epoch time of when to perform the first execution, in milliseconds
	 * @param the amount of milliseconds between each execution
	 */
	public static void setPeriodicOperation(Vertx vertx, Runnable operation, Long firstTime, Long recurrenceEvery) {
		vertx.setTimer(Math.min(1,  firstTime - Instant.now().toEpochMilli()), id1 -> {
			operation.run();
			vertx.setPeriodic(recurrenceEvery, id2 -> {
				operation.run();
			});
		});
	}
	
	private static Long getMilsForNext(LocalTime timeOfDay, ZoneOffset timeZone) {
		Long time = LocalDateTime.now().toLocalDate().atTime(timeOfDay).toEpochSecond(timeZone);
		Long now = Instant.now().getEpochSecond();
		if (time > now)
			return time * 1000;
		return (time * 1000) + TimeUnit.DAYS.toMillis(1);
	}
	
}
