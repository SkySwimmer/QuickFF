package org.asf.quicktools.api.connective;

import org.asf.connective.ContentSource;
import org.asf.connective.handlers.HttpHandlerSet;

public interface IContentSourceInstanceCreator {
	public ContentSource createInstance(HttpHandlerSet handlers, ContentSource parent);
}
