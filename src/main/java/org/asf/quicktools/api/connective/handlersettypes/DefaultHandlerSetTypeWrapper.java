package org.asf.quicktools.api.connective.handlersettypes;

import org.asf.connective.handlers.HttpHandlerSet;
import org.asf.quicktools.api.connective.ConnectiveHandlerSet;
import org.asf.quicktools.api.connective.IHandlerSetInstanceCreator;

@ConnectiveHandlerSet("default")
public class DefaultHandlerSetTypeWrapper extends HttpHandlerSet implements IHandlerSetInstanceCreator {

	@Override
	public HttpHandlerSet createInstance() {
		return new DefaultHandlerSetTypeWrapper();
	}

}
