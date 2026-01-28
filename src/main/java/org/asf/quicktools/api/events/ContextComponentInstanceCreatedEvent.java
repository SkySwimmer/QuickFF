package org.asf.quicktools.api.events;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.api.context.BaseContextComponent;
import org.asf.quicktools.server.BaseControllerServer;

public class ContextComponentInstanceCreatedEvent extends ContextComponentEvent {

	private ConnectiveHttpServer webserver;

	public ContextComponentInstanceCreatedEvent(BaseContextComponent<?> component, BaseContext ctx,
			BaseControllerServer server, ConnectiveHttpServer webserver) {
		super(component, ctx, server);
		this.webserver = webserver;
	}

	public ConnectiveHttpServer getWebserver() {
		return webserver;
	}
}
