package infrastructure.youtube;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dto.youtube.AgeCheckResult;
import dto.youtube.MetaMini;
import dto.youtube.UploadsEntry;
import dto.youtube.VideoInfo;
import infrastructure.http.AppHttp;
import util.time.DurationUtil;

public class YouTubeApiClient {

    private static final String YT_API_BASE = "https://www.googleapis.com/youtube/v3";
    private final HttpClient http = AppHttp.CLIENT;

    /** 年齢制限/埋め込み可否チェック（失敗時は allowed=全件） */
    public AgeCheckResult checkAgeRestriction(String accessToken, List<String> ids) {
        List<String> allowed = new ArrayList<>();
        List<String> ageRestricted = new ArrayList<>();
        List<String> notEmbeddable = new ArrayList<>();

        if (ids == null || ids.isEmpty()) {
            return new AgeCheckResult(allowed, ageRestricted, notEmbeddable);
        }

        try {
            String url = YT_API_BASE + "/videos?part=contentDetails,status&id=" + String.join(",", ids);
            JsonObject root = getJson(url, accessToken);

            JsonArray items = root.getAsJsonArray("items");
            Map<String, JsonObject> byId = new HashMap<>();
            if (items != null) {
                for (JsonElement el : items) {
                    JsonObject it = el.getAsJsonObject();
                    byId.put(it.get("id").getAsString(), it);
                }
            }

            for (String vid : ids) {
                JsonObject it = byId.get(vid);

                boolean age = false;
                boolean emb = true;

                if (it != null) {
                    JsonObject cd = it.has("contentDetails") ? it.getAsJsonObject("contentDetails") : null;
                    if (cd != null && cd.has("contentRating")) {
                        JsonObject cr = cd.getAsJsonObject("contentRating");
                        if (cr.has("ytRating") && "ytAgeRestricted".equals(cr.get("ytRating").getAsString())) {
                            age = true;
                        }
                    }

                    JsonObject st = it.has("status") ? it.getAsJsonObject("status") : null;
                    if (st != null && st.has("embeddable")) {
                        emb = st.get("embeddable").getAsBoolean();
                    }
                }

                if (age) {
                    ageRestricted.add(vid);
                } else if (!emb) {
                    notEmbeddable.add(vid);
                } else {
                    allowed.add(vid);
                }
            }
        } catch (Exception e) {
            // API失敗時は止めない（元コード踏襲）
            allowed.addAll(ids);
        }

        return new AgeCheckResult(allowed, ageRestricted, notEmbeddable);
    }

    /** videos.list: 最小情報取得（oauthToken が無ければ key を利用） */
    public Map<String, MetaMini> fetchMinimal(List<String> ids, String oauthToken) {
        if (ids == null || ids.isEmpty()) return Collections.emptyMap();

        Map<String, MetaMini> out = new HashMap<>();
        for (int i = 0; i < ids.size(); i += 50) {
            List<String> chunk = ids.subList(i, Math.min(i + 50, ids.size()));
            String url = YT_API_BASE + "/videos"
                    + "?part=snippet,contentDetails,status"
                    + "&fields=items(id,snippet(title,tags,channelId,categoryId),contentDetails(duration),status(embeddable))"
                    + "&id=" + enc(String.join(",", chunk));

            try {
                HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET();

                    req.header("Authorization", "Bearer " + oauthToken);

                HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
                if (res.statusCode() != 200) continue;

                JsonArray items = JsonParser.parseString(res.body()).getAsJsonObject().getAsJsonArray("items");
                if (items == null) continue;

                for (JsonElement el : items) {
                    JsonObject it = el.getAsJsonObject();
                    MetaMini m = new MetaMini();

                    m.videoId = it.get("id").getAsString();
                    JsonObject sn = it.getAsJsonObject("snippet");

                    m.title = sn.get("title").getAsString();
                    m.channelId = sn.get("channelId").getAsString();
                    m.categoryId = sn.get("categoryId").getAsInt();

                    JsonElement tg = sn.get("tags");

                    if (tg == null || tg.isJsonNull() || !tg.isJsonArray()) {
                        m.tags = new String[0];
                    } else {
                        JsonArray arr = tg.getAsJsonArray();
                        String[] tags = new String[arr.size()];
                        for (int j = 0; j < arr.size(); j++) {
                            tags[j] = arr.get(j).getAsString();
                        }
                        m.tags = tags;
                    }

                    String iso = it.getAsJsonObject("contentDetails").get("duration").getAsString();
                    m.durationSec = DurationUtil.parseIsoDurationToSeconds(iso);

                    m.embeddable = it.getAsJsonObject("status").get("embeddable").getAsBoolean();

                    out.put(m.videoId, m);
                }
            } catch (Exception ignore) {
                // 元コード踏襲：失敗は無視
            }
        }
        return out;
    }

    /** channels.list: uploads playlist を取得 */
    public Set<UploadsEntry> getUploadsPlaylists(String accessToken, Set<String> channelIds) throws IOException {
        if (channelIds == null || channelIds.isEmpty()) return Set.of();

        Set<UploadsEntry> result = new HashSet<>();
        for (int i = 0; i < channelIds.size(); i += 50) {
        	List<String> ids = new ArrayList<>(channelIds);
            List<String> batch = ids.subList(i, Math.min(i + 50, channelIds.size()));
            String url = YT_API_BASE + "/channels?part=contentDetails&id=" + String.join(",", batch);

            JsonObject root = getJson(url, accessToken);
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) continue;

            for (JsonElement item : items) {
                JsonObject obj = item.getAsJsonObject();
                String chId = obj.has("id") ? obj.get("id").getAsString() : null;

                JsonObject details = obj.has("contentDetails") ? obj.getAsJsonObject("contentDetails") : null;
                JsonObject related = (details != null && details.has("relatedPlaylists"))
                        ? details.getAsJsonObject("relatedPlaylists")
                        : null;

                if (chId != null && related != null && related.has("uploads")) {
                    String uploadsId = related.get("uploads").getAsString();
                    if (uploadsId != null && !uploadsId.isBlank()) {
                        result.add(new UploadsEntry(chId, uploadsId));
                    }
                }
            }
        }
        return result;
    }

    /** playlistItems.list: videoId/title/thumbnailUrl を取得 */
    public Set<VideoInfo> getPlaylistVideos(String accessToken, String playlistId, String channelId) throws IOException {
        Set<VideoInfo> list = new HashSet<>();
        String pageToken = "";

        do {
            String url = YT_API_BASE + "/playlistItems?part=snippet&maxResults=50&playlistId=" + playlistId
                    + (pageToken.isEmpty() ? "" : "&pageToken=" + pageToken);

            JsonObject root = getJson(url, accessToken);
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) break;

            for (JsonElement e : items) {
                JsonObject snippet = e.getAsJsonObject().getAsJsonObject("snippet");
                if (snippet == null) continue;

                JsonObject rid = snippet.getAsJsonObject("resourceId");
                if (rid == null || !rid.has("videoId")) continue;

                String vid = rid.get("videoId").getAsString();
                String title = snippet.has("title") ? snippet.get("title").getAsString() : "";

                String thumb = null;
                if (snippet.has("thumbnails")) {
                    JsonObject thumbs = snippet.getAsJsonObject("thumbnails");
                    if (thumbs.has("default")) {
                        JsonObject def = thumbs.getAsJsonObject("default");
                        if (def.has("url")) {
                            thumb = def.get("url").getAsString();
                        }
                    }
                }

                list.add(new VideoInfo(vid, title, channelId, thumb));
            }

            pageToken = root.has("nextPageToken") ? root.get("nextPageToken").getAsString() : "";
        } while (!pageToken.isEmpty() && list.size() < 2000);

        return list;
    }

    /** videos.list: duration/ratio/viewCount 付与（VideoInfo を更新） */
    public void enrichVideos(String accessToken, Set<VideoInfo> videos) throws IOException {
    	List<VideoInfo> list = new ArrayList<>(videos);
        for (int i = 0; i < videos.size(); i += 50) {
            List<VideoInfo> sub = list.subList(i, Math.min(i + 50, videos.size()));
            String ids = sub.stream().map(v -> v.videoId).collect(Collectors.joining(","));

            String url = YT_API_BASE + "/videos?part=contentDetails,snippet,statistics&id=" + ids;
            JsonObject root = getJson(url, accessToken);

            JsonArray items = root.getAsJsonArray("items");
            if (items == null) continue;

            // O(n^2)回避（元コードは stream.findFirst で重い）
            Map<String, VideoInfo> byId = new HashMap<>();
            for (VideoInfo v : sub) byId.put(v.videoId, v);

            for (JsonElement e : items) {
                JsonObject obj = e.getAsJsonObject();
                String id = obj.get("id").getAsString();
                VideoInfo vi = byId.get(id);
                if (vi == null) continue;

                String durStr = obj.getAsJsonObject("contentDetails").get("duration").getAsString();
                vi.durationSec = DurationUtil.parseIsoDurationToSeconds(durStr);

                try {
                    JsonObject def = obj.getAsJsonObject("snippet")
                            .getAsJsonObject("thumbnails")
                            .getAsJsonObject("default");
                    int width = def.get("width").getAsInt();
                    int height = def.get("height").getAsInt();
                    vi.ratio = (double) height / width;
                } catch (Exception ignore) {}

                try {
                    JsonObject stats = obj.getAsJsonObject("statistics");
                    if (stats != null && stats.has("viewCount")) {
                        vi.playCount = stats.get("viewCount").getAsLong();
                    }
                } catch (Exception ignore) {}
            }
        }
    }

    /** channels.list(snippet): description 取得 */
    public Map<String, String> fetchChannelDescriptions(Set<String> channelIds, String oauthToken) throws IOException {
        if (channelIds == null || channelIds.isEmpty()) return Collections.emptyMap();

        List<String> allIds = new ArrayList<>(channelIds);
        Map<String, String> result = new LinkedHashMap<>();

        for (int i = 0; i < allIds.size(); i += 50) {
            String idsPart = String.join(",", allIds.subList(i, Math.min(i + 50, allIds.size())));
            String url = YT_API_BASE + "/channels?part=snippet&id=" + idsPart;

            JsonObject root = getJson(url, oauthToken);
            JsonArray items = root.getAsJsonArray("items");
            if (items == null) continue;

            for (JsonElement e : items) {
                JsonObject obj = e.getAsJsonObject();
                String id = obj.get("id").getAsString();
                JsonObject snippet = obj.getAsJsonObject("snippet");
                String desc = snippet.has("description") ? snippet.get("description").getAsString() : "";
                result.put(id, desc);
            }
        }
        return result;
    }
    
  

    // ----------------- private helpers -----------------

    private JsonObject getJson(String url, String accessToken) throws IOException {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

            // ここは用途次第で例外化して良いが、元の挙動を崩しにくいように parse だけ行う
            return JsonParser.parseString(resp.body()).getAsJsonObject();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("HTTP interrupted", e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}