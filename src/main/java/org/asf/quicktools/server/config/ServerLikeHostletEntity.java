package org.asf.quicktools.server.config;

import java.io.IOException;

import com.google.gson.JsonObject;

public class ServerLikeHostletEntity extends BaseHostletEntity {

	public String contentSource;

	@Override
	public void loadFromJson(JsonObject source, String scope) throws IOException {
		super.loadFromJson(source, scope);

		// Content source
		if (source.has("contentSource"))
			contentSource = source.get("contentSource").getAsString();
	}

}
