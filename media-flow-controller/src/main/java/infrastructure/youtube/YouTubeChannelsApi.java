package infrastructure.youtube;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import infrastructure.http.AppHttp;

public class YouTubeChannelsApi {
    private static final String CHANNELS_LIST_ENDPOINT =
    		   "https://www.googleapis.com/youtube/v3/channels?part=statistics,snippet,localizations&hl=ja&id=";
    private final HttpClient http = AppHttp.CLIENT;

  //チャンネル登録者数を調べる関数
    public Map<String, JsonObject> fetchChannelStatisticsFromVideoMeta(String token,
  	        Map<String, JsonObject> videoMeta, Map<String, Long> channelRegistrantNumber,
  	      Map<String, String> channel_title_ja_by_id) {
  	    // 1) channelId を重複なく集める
  	    Set<String> channelIds = new LinkedHashSet<>();
  	    for (JsonObject v : videoMeta.values()) {
  	        JsonObject snip = v.has("snippet") && v.get("snippet").isJsonObject()
  	                ? v.getAsJsonObject("snippet") : null;
  	        if (snip != null && snip.has("channelId")) {
  	            String chId = snip.get("channelId").getAsString();
  	            if (chId != null && !chId.isBlank()) channelIds.add(chId);
  	        }
  	    }
  	    if (channelIds.isEmpty()) return Map.of();

  	    // 2) 50件ずつ channels.list
  	    Map<String, JsonObject> out = new HashMap<>();
  	    List<String> all = new ArrayList<>(channelIds);
  	    for (int i = 0; i < all.size(); i += 50) {
  	        List<String> sub = all.subList(i, Math.min(i + 50, all.size()));
  	        String idsCsv = String.join(",", sub);
  	        try {
  	            HttpRequest req = HttpRequest.newBuilder()
  	                .uri(URI.create(CHANNELS_LIST_ENDPOINT + idsCsv))
  	                .timeout(Duration.ofSeconds(20))
  	                .header("Authorization", "Bearer " + token)
  	                .header("Accept", "application/json")
  	                .GET().build();

  	            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  	            if (res.statusCode() / 100 != 2) {
  	                System.out.println("channels.list HTTP" + res.statusCode() + " " + res.body());
  	                continue;
  	            }

  	            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
  	            if (root.has("items") && root.get("items").isJsonArray()) {
  	                for (JsonElement el : root.getAsJsonArray("items")) {
  	                    if (!el.isJsonObject()) continue;
  	                    JsonObject ch = el.getAsJsonObject();

  	                    // ---- id ----
  	                    String chId = ch.has("id") ? ch.get("id").getAsString() : null;

  	                    // ---- statistics ----
  	                    JsonObject stats = ch.has("statistics") && ch.get("statistics").isJsonObject()
  	                            ? ch.getAsJsonObject("statistics") : null;

  	                    if (chId != null && stats != null) {
  	                        out.put(chId, stats); // 統計だけを返り値に（軽量）
  	                        if (!channelRegistrantNumber.containsKey(chId)) {
  	                            long subs = stats.has("subscriberCount")
  	                                    ? Long.parseLong(stats.get("subscriberCount").getAsString())
  	                                    : 0L;
  	                            channelRegistrantNumber.put(chId, subs);
  	                        }
  	                    }

  	                    // ---- titles (snippet.title / localizations.ja.title) ----
  	                    // デフォルト名（英語など）
  	                    String defaultTitle = null;
  	                    if (ch.has("snippet") && ch.get("snippet").isJsonObject()) {
  	                        JsonObject sn = ch.getAsJsonObject("snippet");
  	                        if (sn.has("title") && !sn.get("title").isJsonNull()) {
  	                            defaultTitle = sn.get("title").getAsString();
  	                        }
  	                    }

  	                    // 日本語ローカライズ（存在すれば）
  	                    String jaTitle = null;
  	                    if (ch.has("localizations") && ch.get("localizations").isJsonObject()) {
  	                        JsonObject loc = ch.getAsJsonObject("localizations");
  	                        if (loc.has("ja") && loc.get("ja").isJsonObject()) {
  	                            JsonObject ja = loc.getAsJsonObject("ja");
  	                            if (ja.has("title") && !ja.get("title").isJsonNull()) {
  	                                jaTitle = ja.get("title").getAsString();
  	                            }
  	                        }
  	                    }

  	                    if (chId != null) {
  	                        
  	                        // 日本語タイトル：無ければフォールバックでデフォルトを入れる
  	                        String preferJa = (jaTitle != null && !jaTitle.isBlank())
  	                                ? jaTitle
  	                                : (defaultTitle != null ? defaultTitle : null);

  	                        if (preferJa != null) {
  	                        	channel_title_ja_by_id.put(chId, preferJa);
  	                        }
  	                    }
  	                }
  	            }
  	        } catch (Exception ex) {
  	            System.out.println("channels.list failed: " + ex);
  	        }
  	    }
  	    return out;
  	}
}
