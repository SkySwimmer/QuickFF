package org.asf.quicktools.api.events;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.quicktools.server.BaseControllerServer;

public class StartWebserverEvent extends WebServerEvent {

	public StartWebserverEvent(BaseControllerServer server, ConnectiveHttpServer webserver) {
		super(server, webserver);
	}

}
