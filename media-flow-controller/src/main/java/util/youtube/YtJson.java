package util.youtube;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;

public final class YtJson {
	private YtJson() {
	}

	public static String getStr(JsonObject root, String obj, String key) {
		if (root == null) {
			return null;

		}
		var parent = root.get(obj);
		if (parent == null || !parent.isJsonObject()) {
			return null;
		}
		return getStr(parent.getAsJsonObject(), key);
	}

	public static String getStr(JsonObject o, String key) {
		if (o == null) {
			return null;
		}
		var v = o.get(key);
		if (v == null || v.isJsonNull()) {
			return null;
		}
		return v.isJsonPrimitive() ? v.getAsString() : v.toString();
	}

	public static List<String> getArray(JsonObject root, String obj, String key) {
		List<String> out = new ArrayList<>();
		if (root == null || !root.has(obj) || !root.get(obj).isJsonObject()) {
			return out;
		}
		var o = root.getAsJsonObject(obj);
		if (!o.has(key) || !o.get(key).isJsonArray()) {
			return out;
		}
		for (var e : o.getAsJsonArray(key)) {
			out.add(e.isJsonPrimitive() ? e.getAsString() : e.toString());
		}
		return out;
	}
}