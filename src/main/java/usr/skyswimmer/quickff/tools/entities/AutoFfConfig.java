package usr.skyswimmer.quickff.tools.entities;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import usr.skyswimmer.quicktoolsutils.json.ISerializedJsonEntity;
import usr.skyswimmer.quicktoolsutils.json.JsonUtils;

public class AutoFfConfig implements ISerializedJsonEntity {

	public boolean enabled = false;
	public HashMap<String, String[]> branches = new LinkedHashMap<String, String[]>();
	public HashMap<String, String[]> hardMergeFor = new LinkedHashMap<String, String[]>();

	@Override
	public void loadFromJson(JsonObject source, String scope) throws IOException {
		enabled = JsonUtils.getBooleanOrError(scope, source, "enabled");

		JsonObject branchesList = JsonUtils.getObjectOrError(scope, source, "branches");
		for (String key : branchesList.keySet()) {
			JsonArray arr = JsonUtils.getArrayOrError(scope + " -> branches -> " + key, branchesList.get(key));
			ArrayList<String> branchTargets = new ArrayList<String>();
			for (JsonElement ele : arr) {
				branchTargets.add(JsonUtils.getStringOrError(scope + " -> branches -> " + key, ele));
			}
			branches.put(key, branchTargets.toArray(t -> new String[t]));
		}

		if (source.has("hardMergeFor")) {
			JsonObject hardMergeForList = JsonUtils.getObjectOrError(scope, source, "hardMergeFor");
			for (String key : hardMergeForList.keySet()) {
				JsonArray arr = JsonUtils.getArrayOrError(scope + " -> hardMergeFor -> " + key,
						hardMergeForList.get(key));
				ArrayList<String> branchTargets = new ArrayList<String>();
				for (JsonElement ele : arr) {
					branchTargets.add(JsonUtils.getStringOrError(scope + " -> hardMergeFor -> " + key, ele));
				}
				hardMergeFor.put(key, branchTargets.toArray(t -> new String[t]));
			}
		}
	}

}
