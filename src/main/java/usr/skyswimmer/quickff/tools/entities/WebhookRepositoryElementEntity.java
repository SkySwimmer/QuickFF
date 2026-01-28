package usr.skyswimmer.quickff.tools.entities;

import java.io.IOException;

import com.google.gson.JsonObject;

import usr.skyswimmer.githubwebhooks.api.config.ISerializedJsonEntity;
import usr.skyswimmer.githubwebhooks.api.util.JsonUtils;

public class WebhookRepositoryElementEntity implements ISerializedJsonEntity {

	public String id;

	public String name;
	public String fullName;

	public String gitUrl;
	public String sshUrl;
	public String httpUrl;

	@Override
	public void loadFromJson(JsonObject source, String scope) throws IOException {
		id = JsonUtils.getElementOrError(scope, source, "id").getAsString();
		name = JsonUtils.getElementOrError(scope, source, "name").getAsString();
		fullName = JsonUtils.getElementOrError(scope, source, "full_name").getAsString();
		gitUrl = JsonUtils.getElementOrError(scope, source, "git_url").getAsString();
		sshUrl = JsonUtils.getElementOrError(scope, source, "ssh_url").getAsString();
		httpUrl = JsonUtils.getElementOrError(scope, source, "clone_url").getAsString();
	}

}
