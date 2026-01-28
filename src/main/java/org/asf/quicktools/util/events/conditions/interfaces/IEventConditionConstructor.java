package org.asf.quicktools.util.events.conditions.interfaces;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import org.asf.quicktools.util.events.EventBus;
import org.asf.quicktools.util.events.EventObject;
import org.asf.quicktools.util.events.IEventReceiver;

public interface IEventConditionConstructor {

	/**
	 * Called to construct event conditions
	 * 
	 * @param receiverType Receiver type
	 * @param listener     Event listener method
	 * @param event        Event object
	 * @param annotation   Annotation that was used to add the condition
	 * @param bus          The event bus that is registering the listener
	 * @return IGenericEventCondition instance
	 */
	public IGenericEventCondition construct(IEventReceiver receiverType, Method listener, EventObject event,
			Annotation annotation, EventBus bus);

}
