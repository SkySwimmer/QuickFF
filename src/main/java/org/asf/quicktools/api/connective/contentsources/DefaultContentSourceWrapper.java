package org.asf.quicktools.api.connective.contentsources;

import org.asf.connective.ContentSource;
import org.asf.connective.HandlerSetContentSource;
import org.asf.connective.handlers.HttpHandlerSet;
import org.asf.quicktools.api.connective.ConnectiveContentSource;
import org.asf.quicktools.api.connective.IContentSourceInstanceCreator;

@ConnectiveContentSource("default")
public class DefaultContentSourceWrapper extends HandlerSetContentSource implements IContentSourceInstanceCreator {

	public DefaultContentSourceWrapper() {
		super(null);
	}

	public DefaultContentSourceWrapper(HttpHandlerSet set) {
		super(set);
	}

	@Override
	public ContentSource createInstance(HttpHandlerSet handlers, ContentSource parent) {
		return new DefaultContentSourceWrapper(handlers);
	}

}
