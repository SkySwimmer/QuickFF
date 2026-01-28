package org.asf.quicktools.server.vhost;

import java.util.HashMap;
import java.util.Map;

public class VhostList {

	public VhostDefinition defaultDef;
	public VhostDefinition[] defs;

	public Map<String, VhostDefinition> vhostsByDomain = new HashMap<String, VhostDefinition>();

}
