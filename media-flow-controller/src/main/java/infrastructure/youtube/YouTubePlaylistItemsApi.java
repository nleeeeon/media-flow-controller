package infrastructure.youtube;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import infrastructure.http.AppHttp;

public class YouTubePlaylistItemsApi {

    private static final String YT_API_BASE = "https://www.googleapis.com/youtube/v3";
    private final HttpClient http = AppHttp.CLIENT;


    public LinkedHashSet<String> fetchVideoIdsFromPlaylist(String accessToken, String playlistId) throws IOException {
    	LinkedHashSet<String> result = new LinkedHashSet<>();
        String pageToken = "";

        while (true) {
            StringBuilder url = new StringBuilder();
            url.append(YT_API_BASE).append("/playlistItems")
                    .append("?part=contentDetails")
                    .append("&maxResults=50")
                    .append("&playlistId=").append(enc(playlistId));
            if (!pageToken.isEmpty()) url.append("&pageToken=").append(enc(pageToken));

            JsonObject root = getJson(url.toString(), accessToken);
            JsonArray items = root.getAsJsonArray("items");
            if (items == null || items.size() == 0) break;

            for (var e : items) {
                JsonObject obj = e.getAsJsonObject();
                JsonObject cd = obj.getAsJsonObject("contentDetails");
                if (cd != null && cd.has("videoId")) {
                    String vid = cd.get("videoId").getAsString();
                    if (vid != null && !vid.isBlank()) result.add(vid);
                }
            }

            if (root.has("nextPageToken")) pageToken = root.get("nextPageToken").getAsString();
            else break;
        }
        return result;
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

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}