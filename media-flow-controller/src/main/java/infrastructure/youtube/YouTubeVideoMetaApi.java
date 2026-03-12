package infrastructure.youtube;

import java.io.IOException;
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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import infrastructure.http.AppHttp;
import web.util.UrlUtil;

public class YouTubeVideoMetaApi {

    private static final String YT_API_BASE = "https://www.googleapis.com/youtube/v3";
    private static final String VIDEOS_STATS_ENDPOINT =
		    "https://www.googleapis.com/youtube/v3/videos?part=statistics&id=";
    
    private final HttpClient http = AppHttp.CLIENT;

    /** videos.list を叩いて videoId -> item(JsonObject) を返す */
    public Map<String, JsonObject> fetchVideoMeta(String accessToken, Set<String> videoIds) throws IOException {
        if (videoIds == null || videoIds.isEmpty()) return Map.of();

        List<String> uniq = new ArrayList<>(new LinkedHashSet<>(videoIds));
        Map<String, JsonObject> meta = new HashMap<>();

        for (int i = 0; i < uniq.size(); i += 50) {
            List<String> chunk = uniq.subList(i, Math.min(i + 50, uniq.size()));
            String url = YT_API_BASE + "/videos"
                    + "?part=snippet,contentDetails,topicDetails,status"
                    + "&hl=ja"
                    + "&id=" + UrlUtil.enc(String.join(",", chunk));

            JsonObject root = getJson(url, accessToken);
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) continue;

            for (var el : items) {
                JsonObject item = el.getAsJsonObject();
                if (!item.has("id")) continue;
                String vid = item.get("id").getAsString();
                meta.put(vid, item);
            }
        }
        return meta;
    }
    
  //動画の再生回数を調べる関数
    public Map<String, Long> fetchVideoViewCountFromVideoMeta(
  		    String token, Map<String, JsonObject> videoMeta, Map<String, Long> videoPlayTimes) {

  	  // 1) videoId を重複なく集める（MapのキーがvideoId想定）
  	  Set<String> videoIds = new LinkedHashSet<>(videoMeta.keySet());
  	  if (videoIds.isEmpty()) return Map.of();
  	
  	  // 2) 50件ずつ videos.list(part=statistics)
  	  Map<String, Long> out = new HashMap<>();
  	  List<String> all = new ArrayList<>(videoIds);
  	
  	  for (int i = 0; i < all.size(); i += 50) {
  	    List<String> sub = all.subList(i, Math.min(i + 50, all.size()));
  	    String idsCsv = String.join(",", sub);
  	    try {
  	      HttpRequest req = HttpRequest.newBuilder()
  	          .uri(URI.create(VIDEOS_STATS_ENDPOINT + idsCsv))
  	          .timeout(Duration.ofSeconds(20))
  	          .header("Authorization", "Bearer " + token)
  	          .header("Accept", "application/json")
  	          .GET().build();
  	
  	      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
  	      if (res.statusCode() / 100 != 2) {
  	        System.out.println("videos.list HTTP" + res.statusCode() + " " + res.body());
  	        continue;
  	      }
  	
  	      JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
  	      if (root.has("items") && root.get("items").isJsonArray()) {
  	        for (JsonElement el : root.getAsJsonArray("items")) {
  	          if (!el.isJsonObject()) continue;
  	          JsonObject item = el.getAsJsonObject();
  	
  	          String vid = item.has("id") ? item.get("id").getAsString() : null;
  	          JsonObject stats = (item.has("statistics") && item.get("statistics").isJsonObject())
  	              ? item.getAsJsonObject("statistics") : null;
  	
  	          if (vid != null && stats != null && stats.has("viewCount")) {
  	            try {
  	              long views = Long.parseLong(stats.get("viewCount").getAsString());
  	              out.put(vid, views);
  	              if(!videoPlayTimes.containsKey(vid))videoPlayTimes.put(vid,views);
  	            } catch (NumberFormatException ignore) {
  	              // viewCount が想定外形式のときはスキップ
  	            }
  	          }
  	        }
  	      }
  	    } catch (Exception ex) {
  	      System.out.println("videos.list failed: " + ex);
  	    }
  	  }
  	  return out;
  	}

    private JsonObject getJson(String url, String accessToken) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) throw new IOException("YT API error: " + res.statusCode());
            return JsonParser.parseString(res.body()).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

}