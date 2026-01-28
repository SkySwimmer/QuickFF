package usr.skyswimmer.quickff.tools.entities;

import java.io.IOException;

import com.google.gson.JsonObject;

import usr.skyswimmer.githubwebhooks.api.config.ISerializedJsonEntity;
import usr.skyswimmer.githubwebhooks.api.util.JsonUtils;

public class WebhookInstallationElementEntity implements ISerializedJsonEntity {

	public String id;;

	@Override
	public void loadFromJson(JsonObject source, String scope) throws IOException {
		id = JsonUtils.getElementOrError(scope, source, "id").getAsString();
	}

}
