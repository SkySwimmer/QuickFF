package usr.skyswimmer.quickff.config;

import java.io.IOException;

import usr.skyswimmer.quickff.util.JsonUtils;

import com.google.gson.JsonObject;

public class ServerHostletEntity implements IBaseJsonConfigEntity {

	public ConnectiveAdapterEntity adapter;

	@Override
	public void loadFromJson(JsonObject source, String scope) throws IOException {
		// Adapter
		JsonObject adapter = JsonUtils.getElementOrError(scope, source, "adapter").getAsJsonObject();
		this.adapter = new ConnectiveAdapterEntity();
		this.adapter.loadFromJson(adapter, scope + " -> adapter");
	}

}
