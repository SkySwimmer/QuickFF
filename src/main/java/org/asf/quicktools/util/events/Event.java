package org.asf.quicktools.util.events;

import java.util.ArrayList;
import java.util.function.Consumer;

/**
 * Simple plain event that can be bound to
 * 
 * @param <T> Event object to pass to handlers
 */
public class Event<T extends EventObject> {

	private ArrayList<Consumer<T>> listeners = new ArrayList<Consumer<T>>();

	/**
	 * Adds event handlers
	 * 
	 * @param eventHandler Event handler to add
	 */
	public void addEventHandler(Consumer<T> eventHandler) {
		// Register
		synchronized (listeners) {
			if (!listeners.contains(eventHandler))
				listeners.add(eventHandler);
		}
	}

	/**
	 * Removes event handlers
	 * 
	 * @param eventHandler Event handler to add
	 */
	public void removeEventHandler(Consumer<T> eventHandler) {
		// Add listener
		synchronized (listeners) {
			if (listeners.contains(eventHandler))
				listeners.remove(eventHandler);
		}
	}

	/**
	 * Calls the event
	 * 
	 * @param event Event object to pass
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void dispatchEvent(T event) {
		// Dispatch event
		Consumer<T>[] events;
		synchronized (this.listeners) {
			events = (Consumer<T>[]) this.listeners.toArray(t -> new Consumer<?>[t]);
		}
		for (Consumer ev : events) {
			ev.accept(event);
			if (event.isHandled())
				break;
		}
	}

}
