package org.asf.quicktools.api.context;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.connective.ContentSource;
import org.asf.connective.handlers.HttpHandlerSet;
import org.asf.quicktools.server.BaseControllerServer;
import org.asf.quicktools.util.events.IEventReceiver;

public interface BaseContext extends IEventReceiver {

	/**
	 * Called to instantiate the context
	 * 
	 * @return BaseContext instance
	 */
	public BaseContext createInstance();

	/**
	 * Retrieves the context name
	 * 
	 * @return Context name
	 */
	public String getName();

	/**
	 * Called on server init
	 * 
	 * @param server Server instance
	 */
	public void initServer(BaseControllerServer server);

	/**
	 * Called when the base context instance is initialized (so only called outside
	 * of context elements copied to the actual server instances)
	 * 
	 * @param server Server instance
	 */
	public default void initServerBaseContext(BaseControllerServer server) {
	}

	/**
	 * Called when a HTTP server instance is created
	 * 
	 * @param qFFS   Server instance
	 * @param server Webserver instance
	 */
	public default void initWebserver(BaseControllerServer qFFS, ConnectiveHttpServer server) {
	}

	/**
	 * Called to set up the webserver
	 * 
	 * @param qFFS   Server instance
	 * @param server Webserver instance
	 * @param parent Parent content source (may be null), note: the parent is
	 *               automatically assigned, the instance is only provided for
	 *               convenience
	 * @return ContentSource instance to add to the server
	 */
	public default ContentSource setupWebServer(BaseControllerServer qFFS, ConnectiveHttpServer server,
			ContentSource parent) {
		return null;
	}

	/**
	 * Called to set up the webserver
	 * 
	 * @param qFFS       Server instance
	 * @param server     Webserver instance
	 * @param handlerSet Target handler set
	 */
	public default void setupWebServerHandlers(BaseControllerServer qFFS, ConnectiveHttpServer server,
			HttpHandlerSet handlerSet) {
	}

	/**
	 * Called on server start
	 * 
	 * @param server Server instance
	 */
	public default void startServer(BaseControllerServer server) {
	}

	/**
	 * Called when a HTTP server instance is started
	 * 
	 * @param qFFS   Server instance
	 * @param server Webserver instance
	 */
	public default void startWebserver(BaseControllerServer qFFS, ConnectiveHttpServer server) {
	}

	/**
	 * Called on server stop
	 * 
	 * @param server Server instance
	 */
	public default void stopServer(BaseControllerServer server) {
	}

	/**
	 * Called when a HTTP server instance is stopped
	 * 
	 * @param qFFS   Server instance
	 * @param server Webserver instance
	 */
	public default void stopWebserver(BaseControllerServer qFFS, ConnectiveHttpServer server) {
	}

}
