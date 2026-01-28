package org.asf.quicktools.api.events;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.quicktools.server.BaseControllerServer;

public class WebServerEvent extends ServerEvent {

	private ConnectiveHttpServer webserver;

	public WebServerEvent(BaseControllerServer server, ConnectiveHttpServer webserver) {
		super(server);
		this.webserver = webserver;
	}

	public ConnectiveHttpServer getWebserver() {
		return webserver;
	}
}
