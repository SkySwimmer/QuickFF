package usr.skyswimmer.quickff.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;

import usr.skyswimmer.quickff.util.JsonUtils;

import com.google.gson.JsonObject;

public class ConnectiveAdapterEntity implements IBaseJsonConfigEntity {

	public String protocol;
	public HashMap<String, String> parameters = new LinkedHashMap<String, String>();

	@Override
	public void loadFromJson(JsonObject source, String scope) throws IOException {
		protocol = JsonUtils.getElementOrError(scope, source, "protocol").getAsString();
		parameters.putAll(JsonUtils
				.objectAsStringHashMap(JsonUtils.getElementOrError(scope, source, "parameters").getAsJsonObject()));
	}

}
