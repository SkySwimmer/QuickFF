package org.asf.quicktools.api.events;

import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.server.BaseControllerServer;

public class ContextInitEvent extends ContextEvent {

	public ContextInitEvent(BaseContext ctx, BaseControllerServer server) {
		super(ctx, server);
	}

}
