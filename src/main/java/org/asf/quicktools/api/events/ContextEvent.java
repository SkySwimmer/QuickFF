package org.asf.quicktools.api.events;

import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.server.BaseControllerServer;

public class ContextEvent extends ServerEvent {

	private BaseContext ctx;

	public ContextEvent(BaseContext ctx, BaseControllerServer server) {
		super(server);
		this.ctx = ctx;
	}

	public BaseContext getContext() {
		return ctx;
	}

}
