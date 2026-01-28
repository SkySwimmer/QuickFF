package org.asf.quicktools.server.config;

import java.io.IOException;
import java.util.ArrayList;

import org.asf.quicktools.util.JsonUtils;

import com.google.gson.JsonObject;

public class VhostHostletEntity extends ServerLikeHostletEntity {

	public ArrayList<String> domains = new ArrayList<String>();

	@Override
	public void loadFromJson(JsonObject source, String scope) throws IOException {
		super.loadFromJson(source, scope);

		// Domain
		if (source.has("domain"))
			domains.add(source.get("domain").getAsString());
		else if (source.has("domains"))
			domains.addAll(JsonUtils.stringArrayAsCollection(source.get("domains").getAsJsonArray()));
	}

}
