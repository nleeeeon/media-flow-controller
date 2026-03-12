package infrastructure.youtube;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dto.youtube.VidStat;
import infrastructure.http.AppHttp;
import util.time.TimeUtils;
import web.util.UrlUtil;

public class YouTubeVideosApi {

    private static final String API_KEY = System.getenv("YT_API_KEY");
    
    private final HttpClient http = AppHttp.CLIENT;


    private static class AuthFailed extends RuntimeException {}

    public Map<String, VidStat> fetchStatsForIds(List<String> ids, String oauthTokenOrNull) {
        Map<String, VidStat> out = new ConcurrentHashMap<>();
        if (ids == null || ids.isEmpty()) return out;

        for (int i = 0; i < ids.size(); i += 50) {
            List<String> chunk = ids.subList(i, Math.min(i + 50, ids.size()));
            String base = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=statistics,contentDetails,snippet"
                    + "&id=" + UrlUtil.enc(String.join(",", chunk))
                    + "&fields=items(id,statistics(viewCount),contentDetails(duration),snippet/thumbnails/default(width,height))";

            boolean useOAuth = oauthTokenOrNull != null && !oauthTokenOrNull.isBlank();

            if (useOAuth) {
                try {
                    out.putAll(fetchStatsOnceOAuth(base, oauthTokenOrNull));
                    continue;
                } catch (AuthFailed af) {
                    // fallthrough
                } catch (Exception ignore) {}
            }
            out.putAll(fetchStatsOnceApiKey(base));
        }
        return out;
    }

    public Map<String, VidStat> fetchStatsOnceOAuth(String baseUrl, String token) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + token)
                .GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        int sc = res.statusCode();
        if (sc == 401 || sc == 403) throw new AuthFailed();
        if (sc != 200) return Map.of();
        return parseStats(res.body());
    }

    public Map<String, VidStat> fetchStatsOnceApiKey(String baseUrl) {
        if (API_KEY == null || API_KEY.isBlank()) return Map.of();
        String url = baseUrl + "&key=" + UrlUtil.enc(API_KEY);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return Map.of();
            return parseStats(res.body());
        } catch (Exception e) {
            return Map.of();
        }
    }

    public Map<String, VidStat> parseStats(String json) {
        Map<String, VidStat> map = new HashMap<>();
        JsonArray items = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("items");
        if (items == null) return map;

        for (var el : items) {
            var o = el.getAsJsonObject();
            String id = o.get("id").getAsString();

            long vc = 0L;
            if (o.has("statistics") && o.getAsJsonObject("statistics").has("viewCount")) {
                vc = o.getAsJsonObject("statistics").get("viewCount").getAsLong();
            }

            String iso = o.getAsJsonObject("contentDetails").get("duration").getAsString();
            int sec = TimeUtils.parseIsoDurationToSeconds(iso);

            double ratio = 0.0;
            try {
                var thumb = o.getAsJsonObject("snippet")
                        .getAsJsonObject("thumbnails")
                        .getAsJsonObject("default");
                int w = thumb.get("width").getAsInt();
                int h = thumb.get("height").getAsInt();
                if (w > 0 && h > 0) ratio = (double) h / w;
            } catch (Exception ignore) {}

            map.put(id, new VidStat(vc, sec, ratio));
        }
        return map;
    }

    
    
    public void fetchThumbnails(
            List<String> videoIds,
            String oauthToken,
            Map<String, String> thumbnailMap
    ) throws Exception {

        if (videoIds == null || videoIds.isEmpty()) return;
        if (thumbnailMap == null) throw new IllegalArgumentException("thumbnailMap must not be null");

        for (int i = 0; i < videoIds.size(); i += 50) {
            List<String> batch = videoIds.subList(i, Math.min(i + 50, videoIds.size()));

            String url = "https://www.googleapis.com/youtube/v3/videos"
                    + "?part=snippet"
                    + "&fields=items(id,snippet/thumbnails/default/url)"
                    + "&id=" + String.join(",", batch);

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + oauthToken)
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) continue;

            JsonObject root = JsonParser.parseString(res.body()).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) continue;

            for (var el : items) {
                JsonObject item = el.getAsJsonObject();
                String vid = item.get("id").getAsString();

                JsonObject snippet = item.getAsJsonObject("snippet");
                if (snippet == null) continue;

                String thumbUrl = null;
                try {
                    thumbUrl = snippet.getAsJsonObject("thumbnails")
                            .getAsJsonObject("default")
                            .get("url").getAsString();
                } catch (Exception ignore) {
                }

                if (thumbUrl != null) {
                    thumbnailMap.put(vid, thumbUrl);
                }
            }
        }
    }


    
}