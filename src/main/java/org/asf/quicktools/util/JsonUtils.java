package org.asf.quicktools.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class JsonUtils {

	public static JsonElement getElementOrError(String scope, JsonObject object, String element) throws IOException {
		if (!object.has(element))
			throw new IOException(scope + " misses element " + element);
		return object.get(element);
	}

	public static Collection<String> stringArrayAsCollection(JsonArray arr) {
		ArrayList<String> lst = new ArrayList<String>();
		for (JsonElement ele : arr) {
			lst.add(ele.getAsString());
		}
		return lst;
	}

	public static HashMap<String, String> objectAsStringHashMap(JsonObject arr) {
		HashMap<String, String> lst = new LinkedHashMap<String, String>();
		for (String key : arr.keySet()) {
			lst.put(key, arr.get(key).getAsString());
		}
		return lst;
	}

	public static HashMap<String, JsonObject> objectAsJsonObjectHashMap(JsonObject arr) {
		HashMap<String, JsonObject> lst = new LinkedHashMap<String, JsonObject>();
		for (String key : arr.keySet()) {
			lst.put(key, arr.get(key).getAsJsonObject());
		}
		return lst;
	}

}
