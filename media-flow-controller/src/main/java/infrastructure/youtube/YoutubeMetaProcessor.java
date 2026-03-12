package infrastructure.youtube;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;

import dto.youtube.VideoInput;
import util.youtube.YtJson;

public class YoutubeMetaProcessor {
	public static void replaceTitlesWithLocalized(Map<String, JsonObject> meta, List<VideoInput> rows) {
	    if (meta == null || rows == null) return;

	    for (VideoInput row : rows) {
	        if (row == null || row.videoId == null) continue;

	        JsonObject item = meta.get(row.videoId);
	        if (item == null) continue;

	        JsonObject snippet = item.has("snippet") ? item.getAsJsonObject("snippet") : null;
	        if (snippet == null) continue;

	        // localized.title を優先
	        JsonObject localized = snippet.has("localized") ? snippet.getAsJsonObject("localized") : null;
	        String localizedTitle = YtJson.getStr(localized, "title");

	        if (localizedTitle != null && !localizedTitle.isBlank()) {
	            row.title = localizedTitle.trim();
	        }
	    }
	}
}
