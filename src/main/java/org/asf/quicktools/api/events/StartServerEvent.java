package org.asf.quicktools.api.events;

import org.asf.quicktools.server.BaseControllerServer;

public class StartServerEvent extends ServerEvent {

	public StartServerEvent(BaseControllerServer server) {
		super(server);
	}

}
