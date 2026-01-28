package org.asf.quicktools.util.tasks.promises;

import java.lang.reflect.InvocationTargetException;

import org.asf.quicktools.util.events.Event;
import org.asf.quicktools.util.tasks.TaskCompletedEvent;
import org.asf.quicktools.util.tasks.TaskErroredEvent;

/**
 * 
 * Promise object, used to create simple promises
 * 
 * @author Sky Swimmer
 *
 */
public class Promise<T> {
	private Object lock = new Object();
	private Exception error;
	private boolean run;

	private T result;

	private Event<TaskErroredEvent> errorEvent = new Event<TaskErroredEvent>();
	private Event<TaskCompletedEvent<T>> completedEvent = new Event<TaskCompletedEvent<T>>();

	private Promise() {
	}

	/**
	 * Event error event
	 * 
	 * @return Event instance
	 */
	public Event<TaskErroredEvent> onError() {
		return errorEvent;
	}

	/**
	 * Event completion event
	 * 
	 * @return Event instance
	 */
	public Event<TaskCompletedEvent<T>> onCompleted() {
		return completedEvent;
	}

	/**
	 * Checks if an error occurred
	 * 
	 * @return True if an error was thrown, false otherwise
	 */
	public boolean hasErrored() {
		return error != null;
	}

	/**
	 * Retrieves the error that was thrown
	 * 
	 * @return Exception instance or null
	 */
	public Exception getError() {
		return error;
	}

	/**
	 * Checks if the task has completed
	 * 
	 * @return True if completed, false otherwise
	 */
	public boolean hasCompleted() {
		return run;
	}

	/**
	 * Calls the promise completion events
	 */
	public void callComplete() {
		completedEvent.dispatchEvent(new TaskCompletedEvent<T>(null));
		synchronized (lock) {
			run = true;
			lock.notifyAll();
		}
	}

	/**
	 * Calls the promise completion events
	 */
	public void callComplete(T result) {
		this.result = result;
		completedEvent.dispatchEvent(new TaskCompletedEvent<T>(result));
		synchronized (lock) {
			run = true;
			lock.notifyAll();
		}
	}

	/**
	 * Calls the promise error events
	 */
	public void callError(Exception exception) {
		error = exception;
		errorEvent.dispatchEvent(new TaskErroredEvent(exception));
	}

	/**
	 * Creates a promise
	 * 
	 * @param <T> Return type
	 * @return AsyncTask instance
	 */
	public static <T> Promise<T> createPromise() {
		return new Promise<T>();
	}

	/**
	 * Awaits the result of the task (blocks until completion)
	 * 
	 * @return Result value
	 * @throws InvocationTargetException If the target method causes an exception
	 */
	public T await() throws InvocationTargetException {
		block();
		if (error != null)
			throw new InvocationTargetException(error);
		return getResult();
	}

	/**
	 * Retrieves the result of the task, <b>warning: does not block, only retrieves
	 * the result</b>
	 * 
	 * @return Result value
	 */
	public T getResult() {
		return result;
	}

	/**
	 * Blocks until the task finishes
	 */
	public void block() {
		if (run)
			return;
		synchronized (lock) {
			while (!run) {
				try {
					lock.wait();
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}

}
