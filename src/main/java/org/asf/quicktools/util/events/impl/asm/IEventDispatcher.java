package org.asf.quicktools.util.events.impl.asm;

import org.asf.quicktools.util.events.EventObject;
import org.asf.quicktools.util.events.IEventReceiver;

public interface IEventDispatcher {

	public void dispatch(IEventReceiver receiver, EventObject event);

}
