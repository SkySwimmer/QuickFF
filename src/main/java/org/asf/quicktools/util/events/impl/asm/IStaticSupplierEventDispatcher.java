package org.asf.quicktools.util.events.impl.asm;

import org.asf.quicktools.util.events.SupplierEventObject;

public interface IStaticSupplierEventDispatcher {

	public Object dispatch(SupplierEventObject<?> event);

}
