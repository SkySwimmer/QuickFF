package org.asf.quicktools.util.events.impl.asm;

import org.asf.quicktools.util.events.IEventReceiver;
import org.asf.quicktools.util.events.SupplierEventObject;

public interface ISupplierEventDispatcher {

	public Object dispatch(IEventReceiver receiver, SupplierEventObject<?> event);

}
