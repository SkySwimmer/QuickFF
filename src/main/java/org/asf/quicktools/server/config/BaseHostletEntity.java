package org.asf.quicktools.server.config;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import org.asf.quicktools.util.JsonUtils;

import com.google.gson.JsonObject;

public class BaseHostletEntity implements IBaseJsonConfigEntity {

	public JsonObject sourceJson;

	public String handlerSetType = null;
	public ArrayList<String> contextNames = new ArrayList<String>();
	public HashMap<String, JsonObject> contextParameters = new LinkedHashMap<String, JsonObject>();
	public HashMap<String, String> defaultHeaders = new LinkedHashMap<String, String>();

	@Override
	public void loadFromJson(JsonObject source, String scope) throws IOException {
		this.sourceJson = source;

		// Read context
		if (source.has("context")) {
			contextNames.add(source.get("context").getAsString());
		} else if (source.has("contexts")) {
			contextNames.addAll(JsonUtils.stringArrayAsCollection(source.get("contexts").getAsJsonArray()));
		}

		// Read context parameters
		if (source.has("contextParameters"))
			contextParameters
					.putAll(JsonUtils.objectAsJsonObjectHashMap(source.get("contextParameters").getAsJsonObject()));

		// Read default headers
		if (source.has("defaultHeaders"))
			defaultHeaders.putAll(JsonUtils.objectAsStringHashMap(source.get("defaultHeaders").getAsJsonObject()));

		// Handler set type
		if (source.has("handlerSetType"))
			handlerSetType = source.get("handlerSetType").getAsString();
	}

}
