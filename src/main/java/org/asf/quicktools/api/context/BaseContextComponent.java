package org.asf.quicktools.api.context;

import org.asf.connective.ConnectiveHttpServer;
import org.asf.connective.ContentSource;
import org.asf.connective.handlers.HttpHandlerSet;
import org.asf.quicktools.server.BaseControllerServer;
import org.asf.quicktools.util.events.IEventReceiver;

public interface BaseContextComponent<T extends BaseContext> extends IEventReceiver, IBaseContextReceiver<T> {

	/**
	 * Called when a HTTP server instance is created
	 * 
	 * @param ctx    Context instance
	 * @param qFFS   Server instance
	 * @param server Webserver instance
	 */
	public void initWebserver(T ctx, BaseControllerServer qFFS, ConnectiveHttpServer server);

	/**
	 * Called to set up the webserver
	 * 
	 * @param ctx    Context instance
	 * @param qFFS   Server instance
	 * @param server Webserver instance
	 * @param parent Parent content source (may be null), note: the parent is
	 *               automatically assigned, the instance is only provided for
	 *               convenience
	 * @return ContentSource instance to add to the server
	 */
	public default ContentSource setupWebServer(T ctx, BaseControllerServer qFFS, ConnectiveHttpServer server,
			ContentSource parent) {
		return null;
	}

	/**
	 * Called to set up the webserver
	 * 
	 * @param ctx        Context instance
	 * @param qFFS       Server instance
	 * @param server     Webserver instance
	 * @param handlerSet Target handler set
	 */
	public default void setupWebServerHandlers(T ctx, BaseControllerServer qFFS, ConnectiveHttpServer server,
			HttpHandlerSet handlerSet) {
	}

	/**
	 * Called on server start
	 * 
	 * @param ctx    Context instance
	 * @param server Server instance
	 */
	public default void startServer(T ctx, BaseControllerServer server) {
	}

	/**
	 * Called when a HTTP server instance is started
	 * 
	 * @param ctx    Context instance
	 * @param qFFS   Server instance
	 * @param server Webserver instance
	 */
	public default void startWebserver(T ctx, BaseControllerServer qFFS, ConnectiveHttpServer server) {
	}

	/**
	 * Called on server stop
	 * 
	 * @param ctx    Context instance
	 * @param server Server instance
	 */
	public default void stopServer(T ctx, BaseControllerServer server) {
	}

	/**
	 * Called when a HTTP server instance is stopped
	 * 
	 * @param ctx    Context instance
	 * @param qFFS   Server instance
	 * @param server Webserver instance
	 */
	public default void stopWebserver(T ctx, BaseControllerServer qFFS, ConnectiveHttpServer server) {
	}

}
