package org.asf.quicktools.api.context;

import org.asf.quicktools.server.BaseControllerServer;

public interface IBaseContextReceiver<T extends BaseContext> {

	/**
	 * Called to instantiate the component
	 * 
	 * @return IBaseContextReceiver instance
	 */
	public IBaseContextReceiver<T> createInstance();

	/**
	 * Called when the base context instance is initialized (so only called outside
	 * of context elements copied to the actual server instances)
	 * 
	 * @param ctx    Context instance
	 * @param server Server instance
	 */
	public default void initServerBaseContext(T ctx, BaseControllerServer server) {
	}

	/**
	 * Called on server init
	 * 
	 * @param ctx    Context instance
	 * @param server Server instance
	 */
	public void initServer(T ctx, BaseControllerServer server);

	/**
	 * Called when the component is destroyed
	 * 
	 * @param ctx    Context instance
	 * @param server Server instance
	 */
	public void onDestroy(T ctx, BaseControllerServer server);

}
