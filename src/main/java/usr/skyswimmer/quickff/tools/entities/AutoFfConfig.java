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
	public HashMap<String, Object> hardMergeStrategies = new LinkedHashMap<String, Object>();

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

		if (source.has("hardMergeStrategies")) {
			JsonObject hardMergeStrategiesList = JsonUtils.getObjectOrError(scope, source, "hardMergeStrategies");
			for (String key : hardMergeStrategiesList.keySet()) {
				JsonElement ele = hardMergeStrategiesList.get(key);
				if (ele.isJsonObject()) {
					LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
					JsonObject obj = ele.getAsJsonObject();
					for (String pattern : obj.keySet()) {
						String strat = obj.get(pattern).getAsString();

						// Check validity
						if (!strat.equalsIgnoreCase("ff") && !strat.equalsIgnoreCase("merge")
								&& !strat.equalsIgnoreCase("rebase")
								&& !strat.equalsIgnoreCase("forceff")) {
							// Error
							throw new IOException(scope + " -> hardMergeStrategies -> " + key + " -> " + pattern
									+ " had invalid value: expected one of: ff, rebase, merge, or forceff");
						}

						// Add
						map.put(pattern, strat);
					}
					hardMergeStrategies.put(key, map);
				} else if (ele.isJsonPrimitive()) {
					String strat = ele.getAsString();

					// Check validity
					if (!strat.equalsIgnoreCase("ff") && !strat.equalsIgnoreCase("merge")
							&& !strat.equalsIgnoreCase("rebase")
							&& !strat.equalsIgnoreCase("forceff")) {
						// Error
						throw new IOException(scope + " -> hardMergeStrategies -> " + key
								+ " had invalid value: expected one of: ff, rebase, merge, or forceff");
					}

					hardMergeStrategies.put(key, strat);
				} else {
					throw new IOException(scope + " -> hardMergeStrategies -> " + key
							+ " had invalid value (expected json object or string value)");
				}
			}
		}
	}

}
