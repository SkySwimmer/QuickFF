package org.asf.quicktools.api.events;

import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.api.context.BaseContextComponent;
import org.asf.quicktools.server.BaseControllerServer;

public class ContextComponentEvent extends ServerEvent {

	private BaseContext ctx;
	private BaseContextComponent<?> component;

	public ContextComponentEvent(BaseContextComponent<?> component, BaseContext ctx, BaseControllerServer server) {
		super(server);
		this.ctx = ctx;
		this.component = component;
	}

	public BaseContextComponent<?> getComponent() {
		return component;
	}

	public BaseContext getContext() {
		return ctx;
	}

}
