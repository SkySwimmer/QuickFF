package org.asf.quicktools.api.events;

import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.api.context.BaseContextComponent;
import org.asf.quicktools.server.BaseControllerServer;

public class ContextComponentPreInitEvent extends ContextComponentEvent {

	public ContextComponentPreInitEvent(BaseContextComponent<?> component, BaseContext ctx,
			BaseControllerServer server) {
		super(component, ctx, server);
	}

}
