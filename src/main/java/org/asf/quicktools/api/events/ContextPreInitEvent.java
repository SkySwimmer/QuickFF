package org.asf.quicktools.api.events;

import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.server.BaseControllerServer;

public class ContextPreInitEvent extends ContextEvent {

	public ContextPreInitEvent(BaseContext ctx, BaseControllerServer server) {
		super(ctx, server);
	}

}
