package io.cloudonix.lib;

import java.time.*;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class Timers {
	
	private static final Timer timer = new Timer("cxlib-timer", true);
	
	/**
	 * Public API for canceling future schedules
	 * @author odeda
	 */
	public interface Cancellable {
		/**
		 * Cancel the future schedule
		 * @return true if the cancellation prevents future execution (i.e. return <code>false</code> if 
		 * called on a single-use task that was already run)
		 */
		boolean cancel();
	}
	
	static class RunnableTask extends TimerTask implements Cancellable {
		protected Runnable op;
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
	
	static class RecuringRunnableTask extends RunnableTask implements Cancellable {
		private LocalTime timeOfDay;
		private ZoneOffset timezone;

		private RecuringRunnableTask(Runnable run, LocalTime timeOfDay, ZoneOffset timezone) {
			super(run);
			this.timeOfDay = timeOfDay;
			this.timezone = timezone;
		}
		
		private RecuringRunnableTask(RecuringRunnableTask parent) {
			super(parent.op);
			timeOfDay = parent.timeOfDay;
			timezone = parent.timezone;
		}
		
		@Override
		public void run() {
			try {
				schedule(new RecuringRunnableTask(this), getMilsForNext(timeOfDay, timezone));
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
	public static Cancellable schedule(Runnable operation, long delay) {
		return schedule(new RunnableTask(operation), delay);
	}

	/**
	 * Schedule a one time operation
	 * @param operation the operation to be executed
	 * @param timeUnit Unit to measure the delay in
	 * @param delay number of time units to wait before invoking the operation
	 */
	public static Cancellable schedule(Runnable operation, TimeUnit timeUnit, int delay) {
		return schedule(operation, timeUnit.toMillis(delay));
	}

	/**
	 * Set an operation to happen daily at midnight UTC
	 * 
	 * @param operation the operation to be executed
	 */
	public static Cancellable setDailyOperation(Runnable operation) {
		return setDailyOperation(operation, LocalTime.MIDNIGHT, ZoneOffset.UTC);
	}

	/**
	 * Set an operation to happen daily at a certain time in UTC
	 * 
	 * @param operation the operation to be executed
	 * @param timeOfDay the time of day to execute the operation
	 */
	public static Cancellable setDailyOperation(Runnable operation, LocalTime timeOfDay) {
		return setDailyOperation(operation, timeOfDay, ZoneOffset.UTC);
	}

	/**
	 * Set an operation to happen daily at a certain time
	 * 
	 * @param operation the operation to be executed
	 * @param timeOfDay the time of day to execute the operation
	 * @param timezone the time zone that the time of day is referenced to
	 */
	public static Cancellable setDailyOperation(Runnable operation, LocalTime timeOfDay, ZoneOffset timezone) {
		return schedule(new RecuringRunnableTask(operation, timeOfDay, timezone), getMilsForNext(timeOfDay, timezone));
	}
	
	public static Cancellable schedule(RunnableTask operation, long delay) {
		timer.schedule(operation, delay);
		return operation;
	}
	
	public static Cancellable schedule(RunnableTask operation, Date time) {
		timer.schedule(operation, time);
		return operation;
	}
	
	/**
	 * Set an operation to happen daily at a certain time
	 * @param operation the operation to be executed
	 * @param firstTime the UNIX epoch time of when to perform the first execution, in milliseconds
	 * @param recurrenceEvery the amount of milliseconds between each execution
	 */
	public static Cancellable setPeriodicOperation(Runnable operation, long firstTime, long recurrenceEvery) {
		RunnableTask task = new RunnableTask(operation);
		timer.schedule(task, new Date(firstTime), recurrenceEvery);
		return task;
	}
	
	private static Date getMilsForNext(LocalTime timeOfDay, ZoneOffset timezone) {
		long time = 1000 * LocalDateTime.now().toLocalDate().atTime(timeOfDay).toEpochSecond(timezone);
		long now = Instant.now().toEpochMilli();
		if (time <= now)
			time += TimeUnit.DAYS.toMillis(1);
		return new Date(time);
	}
	
}
