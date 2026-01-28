package org.asf.quicktools.api.events;

import org.asf.quicktools.server.BaseControllerServer;

public class StopServerEvent extends ServerEvent {

	public StopServerEvent(BaseControllerServer server) {
		super(server);
	}

}
