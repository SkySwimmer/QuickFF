package org.asf.quicktools.api.events;

import org.asf.quicktools.server.BaseControllerServer;
import org.asf.quicktools.util.events.EventObject;

public class ServerEvent extends EventObject {
	private BaseControllerServer server;

	public ServerEvent(BaseControllerServer server) {
		this.server = server;
	}

	public BaseControllerServer getServer() {
		return server;
	}

}
