package infrastructure.youtube;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import infrastructure.http.AppHttp;

public class YouTubeSearchApi {

    private final HttpClient http = AppHttp.CLIENT;

    private static class AuthFailed extends RuntimeException {}

    public record SearchHit(String videoId, String title, String channelId, String thumbnailUrl) {}


    public List<SearchHit> searchHits(String query, int maxResults, String token) {
        String url = "https://www.googleapis.com/youtube/v3/search"
                + "?part=snippet&type=video&videoEmbeddable=true"
                + "&maxResults=" + maxResults
                + "&order=relevance&regionCode=JP&relevanceLanguage=ja&safeSearch=none"
                + "&q=" + enc(query)
                + "&fields=items("
                + "id/videoId,"
                + "snippet/title,"
                + "snippet/channelId,"
                + "snippet/thumbnails/default/url,"
                + "snippet/thumbnails/medium/url,"
                + "snippet/thumbnails/high/url"
                + ")";

        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + token)
                    .GET().build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = res.statusCode();
            if (sc == 401 || sc == 403) throw new AuthFailed();
            if (sc != 200) return List.of();

            JsonArray items = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("items");
            if (items == null) return List.of();

            List<SearchHit> out = new ArrayList<>();
            for (var el : items) {
                var o = el.getAsJsonObject();
                String vid = o.getAsJsonObject("id").get("videoId").getAsString();
                var sn = o.getAsJsonObject("snippet");
                String title = sn.get("title").getAsString();
                String chId = sn.get("channelId").getAsString();
                String thumb = pickThumbUrl(sn.getAsJsonObject("thumbnails"));
                out.add(new SearchHit(vid, title, chId, thumb));
            }
            return out;
        } catch (AuthFailed af) {
            throw af;
        } catch (Exception e) {
            return List.of();
        }
    }

   

    private static String pickThumbUrl(JsonObject thumbs) {
        if (thumbs == null) return null;
        try {
            if (thumbs.has("high")) return thumbs.getAsJsonObject("high").get("url").getAsString();
            if (thumbs.has("medium")) return thumbs.getAsJsonObject("medium").get("url").getAsString();
            if (thumbs.has("default")) return thumbs.getAsJsonObject("default").get("url").getAsString();
        } catch (Exception ignore) {}
        return null;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}