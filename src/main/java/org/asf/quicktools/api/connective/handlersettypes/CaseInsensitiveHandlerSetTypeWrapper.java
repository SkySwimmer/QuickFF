package org.asf.quicktools.api.connective.handlersettypes;

import org.asf.connective.handlers.CaseInsensitiveHttpHandlerSet;
import org.asf.connective.handlers.HttpHandlerSet;
import org.asf.quicktools.api.connective.ConnectiveHandlerSet;
import org.asf.quicktools.api.connective.IHandlerSetInstanceCreator;

@ConnectiveHandlerSet("case-insensitive")
public class CaseInsensitiveHandlerSetTypeWrapper extends CaseInsensitiveHttpHandlerSet
		implements IHandlerSetInstanceCreator {

	@Override
	public HttpHandlerSet createInstance() {
		return new CaseInsensitiveHandlerSetTypeWrapper();
	}
}
