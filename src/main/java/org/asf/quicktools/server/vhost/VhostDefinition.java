package org.asf.quicktools.server.vhost;

import java.util.ArrayList;

import org.asf.connective.ContentSource;
import org.asf.quicktools.api.context.BaseContext;
import org.asf.quicktools.server.config.VhostHostletEntity;

public class VhostDefinition {

	public String hostEntryName;
	public VhostHostletEntity config;

	public ArrayList<BaseContext> contexts = new ArrayList<BaseContext>();
	public ContentSource source;

}
