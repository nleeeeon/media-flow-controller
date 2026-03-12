package web.util;

import java.util.Collection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * JSONオブジェクト/配列を組み立てる専用クラス
 * ※ HttpServletResponse には触らない
 */
public final class Jsons {

	private Jsons() {
	}

	/** { "ok": true } */
	public static JsonObject ok() {
		JsonObject o = new JsonObject();
		o.addProperty("ok", true);
		return o;
	}

	public static JsonObject ok(String message) {
		JsonObject o = ok();
		if (message != null && message.length() > 500) {
			message = message.substring(0, 500) + "...";
		}
		o.addProperty("message", message == null ? "" : message);
		return o;
	}

	public static JsonObject okUpload(boolean success, String message) {
		JsonObject o = ok();
		if (message != null && message.length() > 500) {
			message = message.substring(0, 500) + "...";
		}
		o.addProperty("message", message == null ? "" : message);
		o.addProperty("success", success);
		return o;
	}
	

	/** { "ok": false, "code": "...", "message": "..." } */
	public static JsonObject err(String code, String message) {
		JsonObject o = new JsonObject();
		o.addProperty("ok", false);
		o.addProperty("code", code == null ? "" : code);

		// 長すぎるメッセージは軽く丸める（任意）
		if (message != null && message.length() > 500) {
			message = message.substring(0, 500) + "...";
		}
		o.addProperty("message", message == null ? "" : message);
		return o;
	}

	/** ["a","b","c"] */
	public static JsonArray toJsonArray(Collection<String> list) {
		JsonArray a = new JsonArray();
		if (list == null) {
			return a;

		}
		for (String s : list) {
			a.add(s);

		}
		return a;
	}

	/**
	 * {"ok":true, "cached":true/false, "videoIds":[...]}
	 */
	public static JsonObject okVideoIds(Collection<String> ids, boolean cached) {
		JsonObject root = ok();
		root.addProperty("cached", cached);
		root.add("videoIds", toJsonArray(ids));
		return root;
	}

	public static JsonObject notAuth(String authUrl) {
		JsonObject o = new JsonObject();
		o.addProperty("error", "NOT_AUTH");
		o.addProperty("authUrl", authUrl);
		return o;
	}

	public static JsonObject optObj(JsonObject o, String key) {
		if (o == null) {
			return null;
		}
		JsonElement el = o.get(key);
		return (el != null && el.isJsonObject()) ? el.getAsJsonObject() : null;
	}

	public static JsonArray optArray(JsonObject o, String key) {
		if (o == null) {
			return null;
		}
		JsonElement el = o.get(key);
		return (el != null && el.isJsonArray()) ? el.getAsJsonArray() : null;
	}

	public static String optString(JsonObject o, String key, String def) {
		if (o == null) {
			return def;
		}
		JsonElement el = o.get(key);
		if (el == null || el.isJsonNull()) {
			return def;
		}
		try {
			return el.getAsString();
		} catch (Exception e) {
			return def;
		}
	}
}