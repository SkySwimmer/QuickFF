package org.asf.quicktools.util.tasks.scheduling;

import java.util.ArrayList;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asf.quicktools.util.tasks.async.AsyncTask;

/**
 * 
 * Task scheduling system <br/>
 * <br/>
 * Usage:
 * 
 * <pre>
 * <code>
 * import org.asf.nexus.tasks.scheduling.TaskScheduler;
 * import org.asf.nexus.tasks.scheduling.ScheduledTask;
 * import org.asf.nexus.tasks.async.AsyncTask;
 * import static org.asf.nexus.tasks.async.AsyncTask.*;
 * 
 * // Create scheduler
 * TaskScheduler scheduler = new TaskScheduler();
 * 
 * // Tick scheduler in background
 * // If we dont tick the scheduler, well, it will do nothing
 * // This example ticks every 50ms, meaning there are 20 ticks per second
 * runAsync(() -> {
 * 	while(true) {
 * 		// Tick scheduler asynchronously
 * 		runAsync(() -> scheduler.tick());
 * 
 * 		// ...
 * 
 * 		// Wait
 * 		try {
 * 			Thread.sleep(50);
 * 		} catch (InterruptedException e) {
 * 		}
 * 	}
 * });
 * 
 * // 
 * // Ready to use the scheduler, examples below
 * //
 * 
 * // A task that runs on the first tick it can, synchronizing to the server thread
 * // Be careful with synced tick code, it could freeze up the server if too intensive
 * scheduler.oneshot(() -> {
 * 	// Code run on next server tick	
 * 	world.spawnObject(new Label("foo", "bar"));
 * });
 * 
 * // A task that runs on the first tick it can, but asynchronously,
 * // suitable for more intensive operations that could freeze up the server if run synced to the server thread
 * scheduler.oneshotAsync(() -> {
 * 	// Code run on next server tick	asynchronously from the server thread
 * 	// Eg. sendPacketNow sends a packet without pooling, meaning that if the player lags with receiving, the server could lock up if this was synced
 *	player.sendPacketNow(new PlayerCoordinateUpdate(0, 100, 0));
 * });
 * 
 * // A task that is delayed for 10 seconds
 * // Following examples will only show non-async versions of the task types, note that each scheduling method has a Async counterpart you can use as well
 * ScheduledTask task = scheduler.afterSecs(() -> {
 * 	// Run after 10 secs
 * 	for (Player plr : server.getPlayer()) {
 *		plr.sendAnnouncement("Game announcement", "Round #1 begin!", AnnouncementType.TitleNotification);
 * 	}
 * }, 10);
 * 
 * // Cancel the above task before it can run, preventing it from running
 * scheduler.cancel(task);
 *  
 * // There are a few more task types, all of them have async counterparts and return {@link ScheduledTask} instances you can use to view progress, cancel, or interact with as you please
 * // Read the rest of the docs about each task type for details
 * </code>
 * </pre>
 * 
 * @author Sky Swimmer
 * 
 */
public class TaskScheduler {

	private ArrayList<ScheduledTask> tasks = new ArrayList<ScheduledTask>();
	private Logger logger = LogManager.getLogger("TaskScheduler");

	/**
	 * Ticks the task scheduler, required for tasks to be executed, should be run
	 * from either a loop, a server tick event, or a game engine tick/update call
	 */
	public void tick() {
		ArrayList<ScheduledTask> tasksL;
		synchronized (tasks) {
			tasksL = new ArrayList<ScheduledTask>(tasks);
		}

		// Run tasks
		for (ScheduledTask task : tasksL) {
			if ((task.timeWait != -1 && System.currentTimeMillis() - task.timeStart < task.timeWait)
					|| (task.interval != -1 && task.cInterval++ < task.interval))
				continue;

			// Run the action
			if (!task.async) {
				try {
					if (System.getProperty("debugMode") == null) {
						try {
							task.action.run();
							task.completedEvent.dispatchEvent(new ScheduledTaskCompletedEvent());
						} catch (Exception e) {
							logger.error("An error occurred while running a scheduled task", e);
							task.error = e;
							task.errorEvent.dispatchEvent(new ScheduledTaskErroredEvent(e));
							throw e;
						}
					} else
						task.action.run();
				} finally {
					synchronized (task.lock) {
						task.ran = true;
						task.lock.notifyAll();
					}

					// Reset
					task.cInterval = 0;
					task.timeStart = System.currentTimeMillis();

					// Increase count
					if (task.limit != -1)
						task.cCount++;

					// Remove if needed
					if (task.limit != -1 && task.cCount >= task.limit) {
						synchronized (tasks) {
							tasks.remove(task);
						}
					}
				}
			} else {
				AsyncTask.runAsync(() -> {
					try {
						task.action.run();
						task.completedEvent.dispatchEvent(new ScheduledTaskCompletedEvent());
					} catch (Exception e) {
						logger.error("An error occurred while running a scheduled task", e);
						task.error = e;
						task.errorEvent.dispatchEvent(new ScheduledTaskErroredEvent(e));
						throw e;
					} finally {
						synchronized (task.lock) {
							task.ran = true;
							task.lock.notifyAll();
						}
					}
				});

				// Reset
				task.cInterval = 0;
				task.timeStart = System.currentTimeMillis();

				// Increase count
				if (task.limit != -1)
					task.cCount++;

				// Remove if needed
				if (task.limit != -1 && task.cCount >= task.limit) {
					synchronized (tasks) {
						tasks.remove(task);
					}
				}
			}
		}
	}

	/**
	 * Cancels scheduled tasks
	 * 
	 * @param task Task to cancel
	 */
	public void cancel(ScheduledTask task) {
		synchronized (tasks) {
			tasks.remove(task);
		}
	}

	/**
	 * Schedules an action that will run after the given amount of seconds have
	 * passed
	 * 
	 * @param action Action to schedule
	 * @param time   Amount of seconds to wait before running the task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask afterSecs(Runnable action, long time) {
		return afterMs(action, time * 1000);
	}

	/**
	 * Schedules an action that will run after the given amount of seconds have
	 * passed
	 * 
	 * @param action Action to schedule
	 * @param time   Amount of seconds to wait before running the task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask afterSecsAsync(Runnable action, long time) {
		return afterMsAsync(action, time * 1000);
	}

	/**
	 * Schedules an action that will run after the given amount of milliseconds have
	 * passed
	 * 
	 * @param action Action to schedule
	 * @param time   Amount of seconds to wait before running the task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask afterMs(Runnable action, long time) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeStart = System.currentTimeMillis();
		t.timeWait = time;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that will run after the given amount of milliseconds have
	 * passed
	 * 
	 * @param action Action to schedule
	 * @param time   Amount of seconds to wait before running the task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask afterMsAsync(Runnable action, long time) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeStart = System.currentTimeMillis();
		t.timeWait = time;
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs only once
	 * 
	 * @param action Action to schedule
	 * @return ScheduledTask instance
	 */
	public ScheduledTask oneshot(Runnable action) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeStart = System.currentTimeMillis();
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs only once
	 * 
	 * @param action Action to schedule
	 * @return ScheduledTask instance
	 */
	public ScheduledTask oneshotAsync(Runnable action) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeStart = System.currentTimeMillis();
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs only once after a specific amount of ticks have
	 * passed
	 * 
	 * @param action Action to schedule
	 * @param delay  Amount of ticks to wait before running the task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask delayed(Runnable action, int delay) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.interval = delay;
		t.timeStart = System.currentTimeMillis();
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs only once after a specific amount of ticks have
	 * passed
	 * 
	 * @param action Action to schedule
	 * @param delay  Amount of ticks to wait before running the task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask delayedAsync(Runnable action, int delay) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.interval = delay;
		t.timeStart = System.currentTimeMillis();
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a interval
	 * 
	 * @param action   Action to schedule
	 * @param interval Ticks to wait each time before running the action
	 * @return ScheduledTask instance
	 */
	public ScheduledTask interval(Runnable action, int interval) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.interval = interval;
		t.limit = -1;
		t.timeStart = System.currentTimeMillis();
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a interval
	 * 
	 * @param action   Action to schedule
	 * @param interval Ticks to wait each time before running the action
	 * @return ScheduledTask instance
	 */
	public ScheduledTask intervalAsync(Runnable action, int interval) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.interval = interval;
		t.limit = -1;
		t.timeStart = System.currentTimeMillis();
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a second-based interval
	 * 
	 * @param action Action to schedule
	 * @param secs   Seconds to wait each time before running the action
	 * @return ScheduledTask instance
	 */
	public ScheduledTask intervalSecs(Runnable action, long secs) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeWait = secs * 1000;
		t.limit = -1;
		t.timeStart = System.currentTimeMillis();
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a second-based interval
	 * 
	 * @param action Action to schedule
	 * @param secs   Seconds to wait each time before running the action
	 * @return ScheduledTask instance
	 */
	public ScheduledTask intervalSecsAsync(Runnable action, long secs) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeWait = secs * 1000;
		t.limit = -1;
		t.timeStart = System.currentTimeMillis();
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a millisecond-based interval
	 * 
	 * @param action Action to schedule
	 * @param millis Milliseconds to wait each time before running the action
	 * @return ScheduledTask instance
	 */
	public ScheduledTask intervalMs(Runnable action, long millis) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeWait = millis;
		t.limit = -1;
		t.timeStart = System.currentTimeMillis();
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a millisecond-based interval
	 * 
	 * @param action Action to schedule
	 * @param millis Milliseconds to wait each time before running the action
	 * @return ScheduledTask instance
	 */
	public ScheduledTask intervalMsAsync(Runnable action, long millis) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeWait = millis;
		t.limit = -1;
		t.timeStart = System.currentTimeMillis();
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a interval (only a specific amount of times)
	 * 
	 * @param action   Action to schedule
	 * @param interval Ticks to wait each time before running the action
	 * @param limit    The amount of times to run this task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask interval(Runnable action, int interval, int limit) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.interval = interval;
		t.limit = limit;
		t.timeStart = System.currentTimeMillis();
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a interval (only a specific amount of times)
	 * 
	 * @param action   Action to schedule
	 * @param interval Ticks to wait each time before running the action
	 * @param limit    The amount of times to run this task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask intervalAsync(Runnable action, int interval, int limit) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.interval = interval;
		t.limit = limit;
		t.timeStart = System.currentTimeMillis();
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a second-based interval (only a specific
	 * amount of times)
	 * 
	 * @param action Action to schedule
	 * @param secs   Seconds to wait each time before running the action
	 * @param limit  The amount of times to run this task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask intervalSecs(Runnable action, long secs, int limit) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeWait = secs * 1000;
		t.limit = limit;
		t.timeStart = System.currentTimeMillis();
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a second-based interval (only a specific
	 * amount of times)
	 * 
	 * @param action Action to schedule
	 * @param secs   Seconds to wait each time before running the action
	 * @param limit  The amount of times to run this task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask intervalSecsAsync(Runnable action, long secs, int limit) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeWait = secs * 1000;
		t.limit = limit;
		t.timeStart = System.currentTimeMillis();
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a millisecond-based interval (only a
	 * specific amount of times)
	 * 
	 * @param action Action to schedule
	 * @param millis Milliseconds to wait each time before running the action
	 * @param limit  The amount of times to run this task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask intervalMs(Runnable action, long millis, int limit) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeWait = millis;
		t.limit = limit;
		t.timeStart = System.currentTimeMillis();
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on a millisecond-based interval (only a
	 * specific amount of times)
	 * 
	 * @param action Action to schedule
	 * @param millis Milliseconds to wait each time before running the action
	 * @param limit  The amount of times to run this task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask intervalMsAsync(Runnable action, long millis, int limit) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.timeWait = millis;
		t.limit = limit;
		t.timeStart = System.currentTimeMillis();
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on every tick
	 * 
	 * @param action Action to schedule
	 * @return ScheduledTask instance
	 */
	public ScheduledTask repeat(Runnable action) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.limit = -1;
		t.timeStart = System.currentTimeMillis();
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on every tick
	 * 
	 * @param action Action to schedule
	 * @return ScheduledTask instance
	 */
	public ScheduledTask repeatAsync(Runnable action) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.limit = -1;
		t.timeStart = System.currentTimeMillis();
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on every tick until its limit is reached
	 * 
	 * @param action Action to schedule
	 * @param limit  Amount of times to run this task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask repeat(Runnable action, int limit) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.limit = limit;
		t.timeStart = System.currentTimeMillis();
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

	/**
	 * Schedules an action that runs on every tick until its limit is reached
	 * 
	 * @param action Action to schedule
	 * @param limit  Amount of times to run this task
	 * @return ScheduledTask instance
	 */
	public ScheduledTask repeatAsync(Runnable action, int limit) {
		ScheduledTask t = new ScheduledTask();
		t.action = action;
		t.limit = limit;
		t.timeStart = System.currentTimeMillis();
		t.async = true;
		synchronized (tasks) {
			tasks.add(t);
		}
		return t;
	}

}
