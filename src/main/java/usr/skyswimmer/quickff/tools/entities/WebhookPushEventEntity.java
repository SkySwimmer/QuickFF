package usr.skyswimmer.quickff.tools.entities;

import java.io.IOException;

import com.google.gson.JsonObject;

import usr.skyswimmer.quicktoolsutils.json.ISerializedJsonEntity;
import usr.skyswimmer.quicktoolsutils.json.JsonUtils;

public class WebhookPushEventEntity implements ISerializedJsonEntity {

	public String ref;
	public String baseRef;

	public String before;
	public String after;

	public WebhookInstallationElementEntity installation;
	public WebhookRepositoryElementEntity repository;

	@Override
	public void loadFromJson(JsonObject source, String scope) throws IOException {
		ref = JsonUtils.getElementOrError(scope, source, "ref").getAsString();
		baseRef = JsonUtils.stringOrNull(JsonUtils.getElementOrError(scope, source, "base_ref"));
		before = JsonUtils.getElementOrError(scope, source, "before").getAsString();
		after = JsonUtils.getElementOrError(scope, source, "after").getAsString();
		repository = new WebhookRepositoryElementEntity();
		repository.loadFromJson(JsonUtils.getElementOrError(scope, source, "repository").getAsJsonObject(),
				scope + " -> repository");
		if (source.has("installation")) {
			installation = new WebhookInstallationElementEntity();
			installation.loadFromJson(source.get("installation").getAsJsonObject(), scope + " -> installation");
		}
	}

}
