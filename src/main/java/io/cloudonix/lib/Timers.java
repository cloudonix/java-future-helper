package io.cloudonix.lib;

import java.time.*;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class Timers {
	
	private static final Timer timer = new Timer("cxlib-timer", true);
	
	static class RunnableTask extends TimerTask {
		private Runnable op;
		private RunnableTask(Runnable run) {
			op = run;
		}
		@Override
		public void run() {
			try {
				op.run();
			} catch (Throwable t) {
				java.util.logging.Logger.getLogger(op.getClass().toString()).severe("Error in timer task: " + t);
				t.printStackTrace();
			}
		}
	}
	
	static class RecuringRunnableTask extends TimerTask {
		private Runnable op;
		private LocalTime timeOfDay;
		private ZoneOffset timezone;

		private RecuringRunnableTask(Runnable run, LocalTime timeOfDay, ZoneOffset timezone) {
			op = run;
			this.timeOfDay = timeOfDay;
			this.timezone = timezone;
		}
		
		private RecuringRunnableTask(RecuringRunnableTask parent) {
			op = parent.op;
			timeOfDay = parent.timeOfDay;
			timezone = parent.timezone;
		}
		
		@Override
		public void run() {
			try {
				timer.schedule(new RecuringRunnableTask(this), getMilsForNext(timeOfDay, timezone));
				op.run();
			} catch (Throwable t) {
				java.util.logging.Logger.getLogger(op.getClass().toString()).severe("Error in timer task: " + t);
				t.printStackTrace();
			}
		}
	}

	/**
	 * Schedule a one time operation
	 * @param operation the operation to be executed
	 * @param delay number of milliseconds to wait before invoking the operation
	 */
	public static void schedule(Runnable operation, long delay) {
		timer.schedule(new RunnableTask(operation), delay);
	}

	/**
	 * Schedule a one time operation
	 * @param operation the operation to be executed
	 * @param timeUnit Unit to measure the delay in
	 * @param delay number of time units to wait before invoking the operation
	 */
	public static void schedule(Runnable operation, TimeUnit timeUnit, int delay) {
		timer.schedule(new RunnableTask(operation), timeUnit.toMillis(delay));
	}

	/**
	 * Set an operation to happen daily at midnight UTC
	 * 
	 * @param operation the operation to be executed
	 */
	public static void setDailyOperation(Runnable operation) {
		setDailyOperation(operation, LocalTime.MIDNIGHT, ZoneOffset.UTC);
	}

	/**
	 * Set an operation to happen daily at a certain time in UTC
	 * 
	 * @param operation the operation to be executed
	 * @param timeOfDay the time of day to execute the operation
	 */
	public static void setDailyOperation(Runnable operation, LocalTime timeOfDay) {
		setDailyOperation(operation, timeOfDay, ZoneOffset.UTC);
	}

	/**
	 * Set an operation to happen daily at a certain time
	 * 
	 * @param operation the operation to be executed
	 * @param timeOfDay the time of day to execute the operation
	 * @param timezone the time zone that the time of day is referenced to
	 */
	public static void setDailyOperation(Runnable operation, LocalTime timeOfDay, ZoneOffset timezone) {
		timer.schedule(new RecuringRunnableTask(operation, timeOfDay, timezone), getMilsForNext(timeOfDay, timezone));
	}
	
	/**
	 * Set an operation to happen daily at a certain time
	 * @param operation the operation to be executed
	 * @param firstTime the UNIX epoch time of when to perform the first execution, in milliseconds
	 * @param recurrenceEvery the amount of milliseconds between each execution
	 */
	public static void setPeriodicOperation(Runnable operation, long firstTime, long recurrenceEvery) {
		timer.schedule(new RunnableTask(operation), new Date(firstTime), recurrenceEvery);
	}
	
	private static Date getMilsForNext(LocalTime timeOfDay, ZoneOffset timezone) {
		long time = 1000 * LocalDateTime.now().toLocalDate().atTime(timeOfDay).toEpochSecond(timezone);
		long now = Instant.now().toEpochMilli();
		if (time <= now)
			time += TimeUnit.DAYS.toMillis(1);
		return new Date(time);
	}
	
}
