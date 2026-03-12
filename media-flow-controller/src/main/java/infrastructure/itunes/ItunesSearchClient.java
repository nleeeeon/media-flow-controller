package infrastructure.itunes;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dto.itunes.ArtistHit;
import infrastructure.http.AppHttp;
import infrastructure.http.WindowRateLimiter;

public final class ItunesSearchClient {

    private static final String ITUNES_SEARCH = "https://itunes.apple.com/search";

    private final HttpClient http = AppHttp.CLIENT;
    private final WindowRateLimiter limiter;
    private final String userAgent;
    private final Duration timeout;

    public ItunesSearchClient(WindowRateLimiter limiter, String userAgent, Duration timeout) {
        this.limiter = limiter;
        this.userAgent = userAgent;
        this.timeout = timeout;
    }

    public Optional<ArtistHit> searchTopArtist(String term, String country) {
        limiter.acquire();

        try {
            String url = ITUNES_SEARCH + "?"
                    + "media=music"
                    + "&entity=musicArtist"
                    + "&attribute=artistTerm"
                    + "&limit=5"
                    + "&country=" + enc(country)
                    + "&term=" + enc(term);

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeout)
                    .header("User-Agent", userAgent)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return Optional.empty();

            JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
            JsonArray results = root.has("results") && root.get("results").isJsonArray()
                    ? root.getAsJsonArray("results")
                    : new JsonArray();

            if (results.isEmpty()) return Optional.empty();

            JsonObject first = results.get(0).getAsJsonObject();
            String artistName = getString(first, "artistName");
            if (artistName == null || artistName.isBlank()) return Optional.empty();

            Long artistId = getLong(first, "artistId");
            Integer amgArtistId = getInt(first, "amgArtistId");

            return Optional.of(new ArtistHit(artistId, amgArtistId, artistName));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    private static String getString(JsonObject o, String key) {
        return (o.has(key) && o.get(key).isJsonPrimitive()) ? o.get(key).getAsString() : null;
    }

    private static Long getLong(JsonObject o, String key) {
        try {
            return (o.has(key) && o.get(key).isJsonPrimitive()) ? o.get(key).getAsLong() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer getInt(JsonObject o, String key) {
        Long v = getLong(o, key);
        return v == null ? null : v.intValue();
    }
}