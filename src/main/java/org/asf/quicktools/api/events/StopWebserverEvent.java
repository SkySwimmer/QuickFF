package org.asf.quicktools.api.events;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.quicktools.server.BaseControllerServer;

public class StopWebserverEvent extends WebServerEvent {

	public StopWebserverEvent(BaseControllerServer server, ConnectiveHttpServer webserver) {
		super(server, webserver);
	}

}
