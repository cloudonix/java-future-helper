package io.cloudonix.lib;

import java.time.*;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class Timers {
	
	private static final TimerHolder timer = new TimerHolder();
	private static class TimerHolder {
		private Timer timer;
		
		public TimerHolder() {
			reset();
		}
		
		private void reset() {
			if (timer != null)
				timer.cancel();
			timer = new Timer("cxlib-timer", true);
		}
		
		public void scheduleAtFixedRate(TimerTask task, long start, long period) {
			try {
				timer.scheduleAtFixedRate(task, start, period);
			} catch (IllegalStateException e) {
				java.util.logging.Logger.getLogger(task.getClass().toString()).severe("Timer thread gone while running! trying to restart");
				reset();
				scheduleAtFixedRate(task, start, period);
			}
		}

		public void schedule(RunnableTask operation, long delay) {
			try {
				timer.schedule(operation, delay);
			} catch (IllegalStateException e) {
				java.util.logging.Logger.getLogger(operation.getClass().toString()).severe("Timer thread gone while running! trying to restart");
				reset();
				schedule(operation, delay);
			}
		}

		public void schedule(RunnableTask operation, Date time) {
			try {
				timer.schedule(operation, time);
			} catch (IllegalStateException e) {
				java.util.logging.Logger.getLogger(operation.getClass().toString()).severe("Timer thread gone while running! trying to restart");
				reset();
				schedule(operation, time);
			}
		}
	};
	
	/**
	 * Public API for canceling future schedules
	 * @author odeda
	 */
	@FunctionalInterface
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
		return schedule(new RunnableTask(operation), timeUnit.toMillis(delay));
	}

	/**
	 * Schedule a one time operation
	 * @param operation the operation to be executed
	 * @param delay duration to delay the execution by
	 */
	public static Cancellable schedule(Runnable operation, Duration delay) {
		return schedule(new RunnableTask(operation), delay.toMillis());
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
	 * @param firstTime the time at which to perform the first execution, in milliseconds.
	 * 	If null or in the past, the action will be performed immediately.
	 * @param recurrenceEvery the delay between each execution
	 */
	public static Cancellable setPeriodicOperation(Runnable operation, Instant firstTime, Duration recurrenceEvery) {
		RunnableTask task = new RunnableTask(operation);
		long start = firstTime == null ? 0 : Math.max(0, firstTime.toEpochMilli() - System.currentTimeMillis());
		timer.scheduleAtFixedRate(task, start, recurrenceEvery.toMillis());
		return task;
	}
	
	/**
	 * Set an operation to happen daily at a certain time
	 * @param operation the operation to be executed
	 * @param firstTime the UNIX epoch time of when to perform the first execution, in milliseconds
	 * @param recurrenceEvery the amount of milliseconds between each execution
	 */
	public static Cancellable setPeriodicOperation(Runnable operation, long firstTime, long recurrenceEvery) {
		RunnableTask task = new RunnableTask(operation);
		timer.scheduleAtFixedRate(task, Math.max(0,  firstTime - System.currentTimeMillis()), recurrenceEvery);
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
