package org.asf.quicktools.util.events.conditions.interfaces;

import java.lang.reflect.Method;

import org.asf.quicktools.util.events.EventObject;
import org.asf.quicktools.util.events.IEventReceiver;

public interface IEventCondition<T extends EventObject> extends IGenericEventCondition {

	/**
	 * Indicates if the condition supports static event listeners
	 * 
	 * @return True if static listeners are supported, false otherwise
	 */
	public default boolean supportsStatic() {
		return false;
	}

	/**
	 * Defines the event type
	 * 
	 * @return Event type class
	 */
	public Class<T> eventType();

	/**
	 * Checks if the event matches this condition
	 * 
	 * @param receiverType Receiver type
	 * @param listener     Event listener
	 * @param event        Event object
	 * @return True if valid, false otherwise
	 */
	public boolean matching(IEventReceiver receiverType, Method listener, T event);

	@Override
	@SuppressWarnings("unchecked")
	public default boolean match(IEventReceiver receiverType, Method listener, EventObject event) {
		if (eventType().isAssignableFrom(event.getClass()))
			return matching(receiverType, listener, (T) event);
		return true;
	}

}
