package org.asf.quicktools.server.config;

import java.io.IOException;
import java.util.ArrayList;

import org.asf.quicktools.util.JsonUtils;

import com.google.gson.JsonObject;

public class ServerHostletEntity extends ServerLikeHostletEntity {

	public ConnectiveAdapterEntity adapter;
	public ArrayList<String> vhostSets = new ArrayList<String>();

	@Override
	public void loadFromJson(JsonObject source, String scope) throws IOException {
		super.loadFromJson(source, scope);

		// VHost Sets
		if (source.has("vhostSet"))
			vhostSets.add(source.get("vhostSet").getAsString());
		else if (source.has("vhostSets"))
			vhostSets.addAll(JsonUtils.stringArrayAsCollection(source.get("vhostSets").getAsJsonArray()));

		// Adapter
		JsonObject adapter = JsonUtils.getElementOrError(scope, source, "adapter").getAsJsonObject();
		this.adapter = new ConnectiveAdapterEntity();
		this.adapter.loadFromJson(adapter, scope + " -> adapter");
	}

}
