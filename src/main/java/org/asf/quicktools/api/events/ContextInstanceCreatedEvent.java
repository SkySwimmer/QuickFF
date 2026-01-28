package org.asf.quicktools.api.events;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.server.BaseControllerServer;

public class ContextInstanceCreatedEvent extends ContextEvent {

	private ConnectiveHttpServer webserver;

	public ContextInstanceCreatedEvent(BaseContext ctx, BaseControllerServer server, ConnectiveHttpServer webserver) {
		super(ctx, server);
		this.webserver = webserver;
	}

	public ConnectiveHttpServer getWebserver() {
		return webserver;
	}

}
