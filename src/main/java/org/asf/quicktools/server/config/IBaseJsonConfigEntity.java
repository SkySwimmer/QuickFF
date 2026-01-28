package org.asf.quicktools.server.config;

import java.io.IOException;

import com.google.gson.JsonObject;

public interface IBaseJsonConfigEntity {

	public void loadFromJson(JsonObject source, String scope) throws IOException;

}
