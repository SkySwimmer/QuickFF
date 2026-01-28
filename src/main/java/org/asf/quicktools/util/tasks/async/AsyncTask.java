package org.asf.quicktools.util.tasks.async;

import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.asf.quicktools.util.events.Event;
import org.asf.quicktools.util.tasks.TaskCompletedEvent;
import org.asf.quicktools.util.tasks.TaskErroredEvent;

/**
 * 
 * Async task object - used to create asynchronous methods and tasks<br/>
 * <br/>
 * Usage:
 * 
 * <pre>
 * <code>
 * // Import async utilities, highly recommended import for coding ease
 * import org.asf.nexus.tasks.async.AsyncTask;
 * import static org.asf.nexus.tasks.async.AsyncTask.*;
 * 
 * private static void main(String[] args) {
 * 	// ...
 * 
 *	// Download something asychronously
 *	runAsync(downloadAsync("https://example.com"), res -> {
 *		// Called after download finishes
 *		runAsync(() -> {
 *			// Code in runAsync() blocks is run asynchronously
 *
 *			// Call another async method and use block() to wait for its completion
 *			// However, because we aren't using runAsync, block() forces it to run synchronously since its not scheduled for async operation
 *			setHtmlAsync(res.body, webBrowser).block();
 *		})
 *	});
 *
 * 	// ...
 * 	// Code after runAsync, unless block() or await(), is called runs while the download is busy because the download is asynchronous
 * }
 * 
 * // Async download
 * private static AsyncTask&lt;HttpResponse&gt; downloadAsync(String url) {
 *	// The call return createTask() is key to making asynchronous methods
 *	// It creates, but doesnt run, a new task object from a runnable/supplier block
 * 	return createTask(() -> {
 * 		// Async method code
 * 		// Here you can write the code of your method
 *		// Beware that any code outside of createTask runs synchronously to the caller of downloadAsync() which would be problematic
 *		
 *		HttpClient client = new HttpClient(...);
 *		// ... headers, method etc
 *		// ... try catch etc for url
 *		client.setUrl(new URL(url));
 *
 *		// Use await to run a async method and wait for its result/completion
 *		// Outside of async code, this spins up a async task thread and uses await(), in async code it waits for the result, calling it synchronously
 *		// If you wish it to run async, you can do await(runAsync(...)) instead
 *		HttpResponse res = await(client.getAsync());
 *
 *		// Return
 *		// Returning inside an async task just sets the result for await(), callbacks and await etc
 *		return res;
 * 	});
 * }
 * 
 * // Async webbrowser load
 * // This is a async void without result objects
 * private static AsyncTask&lt;Void&gt; setHtmlAsync(String body, WebBrowser browser) {
 * 	return createTask(() -> {
 * 		// ...
 * 
 * 		// Load document 
 * 		Document dom = parseDom(body);
 * 
 * 		// Load
 * 		browser.load(dom);
 * 		
 * 		// Render
 * 		browser.render();
 * 		
 * 		// ...
 * 		// No return call needed
 * 	});
 * }
 * </code>
 * </pre>
 * 
 * @author Sky Swimmer
 *
 */
public class AsyncTask<T> {
	private Supplier<T> action;
	private Runnable actionR;
	private boolean run;
	private Object lock = new Object();
	private Exception error;
	private T result;
	boolean running;
	boolean slatedForAsyncRun;

	private Event<TaskErroredEvent> errorEvent = new Event<TaskErroredEvent>();
	private Event<TaskCompletedEvent<T>> completedEvent = new Event<TaskCompletedEvent<T>>();

	private AsyncTask() {
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
	 * Awaits tasks
	 * 
	 * @param <T>  Return type
	 * @param task Task to await
	 * @return Task result
	 * @throws InvocationTargetException If the target method causes an exception
	 */
	public static <T> T await(AsyncTask<T> task) throws InvocationTargetException {
		// Check current thread
		if (Thread.currentThread() instanceof AsyncTaskThreadHandler)
			return task.await();

		// Run async
		return runAsync(task).await();
	}

	/**
	 * Runs a task asynchronously
	 * 
	 * @param <T>  Return type
	 * @param task Task to run
	 * @return AsyncTask instance
	 */
	public static <T> AsyncTask<T> runAsync(AsyncTask<T> task) {
		return AsyncTaskManager.runAsync(task);
	}

	/**
	 * Runs a task asynchronously
	 * 
	 * @param task     Task to run
	 * @param callback Callback to run when the task finishes
	 * @return AsyncTask instance
	 */
	public static AsyncTask<Void> runAsync(AsyncTask<Void> task, Runnable callback, Consumer<Exception> errorCallback) {
		return runAsync(() -> {
			task.block();
			if (task.hasErrored()) {
				errorCallback.accept(task.getError());
				return;
			}
			callback.run();
		});
	}

	/**
	 * Runs a task asynchronously
	 * 
	 * @param <T>      Return type
	 * @param task     Task to run
	 * @param callback Callback to run when the task finishes
	 * @return AsyncTask instance
	 */
	public static <T> AsyncTask<T> runAsync(AsyncTask<T> task, Consumer<T> callback,
			Consumer<Exception> errorCallback) {
		return runAsync(() -> {
			task.block();
			if (task.hasErrored()) {
				errorCallback.accept(task.getError());
				throw new RuntimeException("Exception in target tasks", task.getError());
			}
			T res = task.getResult();
			callback.accept(res);
			return res;
		});
	}

	/**
	 * Runs a runnable asynchronously
	 * 
	 * @param action Action to run
	 * @return AsyncTask instance
	 */
	public static AsyncTask<Void> runAsync(Runnable action) {
		return AsyncTaskManager.runAsync(action);
	}

	/**
	 * Runs a runnable asynchronously
	 * 
	 * @param action   Action to run
	 * @param callback Method to run on completion
	 * @return AsyncTask instance
	 */
	public static AsyncTask<Void> runAsync(Runnable action, Runnable callback, Consumer<Exception> errorCallback) {
		return runAsync(AsyncTask.createTask(() -> {
			action.run();
		}), callback, errorCallback);
	}

	/**
	 * Runs a supplier asynchronously
	 * 
	 * @param <T>    Return type
	 * @param action Action to run
	 * @return AsyncTask instance
	 */
	public static <T> AsyncTask<T> runAsync(Supplier<T> action) {
		return AsyncTaskManager.runAsync(action);
	}

	/**
	 * Runs a supplier asynchronously
	 * 
	 * @param <T>      Return type
	 * @param action   Action to run
	 * @param callback Method to run on completion
	 * @return AsyncTask instance
	 */
	public static <T> AsyncTask<T> runAsync(Supplier<T> action, Consumer<T> callback,
			Consumer<Exception> errorCallback) {
		return runAsync(AsyncTask.createTask(() -> {
			return action.get();
		}), callback, errorCallback);
	}

	/**
	 * Creates an async task object
	 * 
	 * @param <T>    Return type
	 * @param action Action to run
	 * @return AsyncTask instance
	 */
	public static <T> AsyncTask<T> createTask(Supplier<T> action) {
		AsyncTask<T> task = new AsyncTask<T>();
		task.action = action;
		return task;
	}

	/**
	 * Creates an async task object
	 * 
	 * @param action Action to run
	 * @return AsyncTask instance
	 */
	public static AsyncTask<Void> createTask(Runnable action) {
		AsyncTask<Void> task = new AsyncTask<Void>();
		task.actionR = action;
		return task;
	}

	void run() {
		try {
			running = true;
			if (action != null)
				result = action.get();
			else
				actionR.run();
			completedEvent.dispatchEvent(new TaskCompletedEvent<T>(result));
		} catch (Exception e) {
			error = e;
			errorEvent.dispatchEvent(new TaskErroredEvent(e));
			throw e;
		} finally {
			// Release
			synchronized (lock) {
				run = true;
				lock.notifyAll();
			}
		}
	}

	/**
	 * Runs the the task synchronously
	 */
	public void execute() {
		if (slatedForAsyncRun || run || running)
			return;
		run();
	}

	/**
	 * Checks if the task has been started
	 * 
	 * @return True if started, false otherwise
	 */
	public boolean hasStarted() {
		return running;
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
		if (!running && !slatedForAsyncRun)
			execute();
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
