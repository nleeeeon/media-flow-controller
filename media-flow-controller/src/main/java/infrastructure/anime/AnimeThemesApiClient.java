package infrastructure.anime;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dto.anime.AnimeOpEd;
import dto.anime.Theme;
import infrastructure.http.AppHttp;
import infrastructure.http.SimpleRateLimiter;
import web.util.Jsons;

public final class AnimeThemesApiClient {

    private static final String BASE = "https://api.animethemes.moe/anime";

    private final HttpClient http = AppHttp.CLIENT;
    private final SimpleRateLimiter rateLimiter;

    private final String userAgent;
    private final long safeIntervalMs;
    private final int maxRetries;

    public AnimeThemesApiClient(SimpleRateLimiter rateLimiter,
                               String userAgent,
                               long safeIntervalMs,
                               int maxRetries) {
        this.rateLimiter = rateLimiter;
        this.userAgent = userAgent;
        this.safeIntervalMs = safeIntervalMs;
        this.maxRetries = maxRetries;
    }

    /** タイトルから最初の1件を取得（見つからない場合 empty） */
    public Optional<AnimeOpEd> fetchOneAnime(String title) throws Exception {
        if (title == null || title.length() < 2) return Optional.empty();

        String include = "animethemes,animethemes.song,animethemes.song.artists,animethemes.animethemeentries";
        String url = BASE
                + "?q=" + enc(title)
                + "&page[size]=1"
                + "&page[number]=1"
                + "&include=" + enc(include);

        int attempt = 0;
        while (true) {
            attempt++;

            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.api+json")
                    .header("User-Agent", userAgent)
                    .GET()
                    .build();

            rateLimiter.acquire();

            Instant t0 = Instant.now();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            long elapsed = Duration.between(t0, Instant.now()).toMillis();

            int status = resp.statusCode();
            Optional<String> reset = resp.headers().firstValue("X-RateLimit-Reset");
            Optional<String> ct = resp.headers().firstValue("Content-Type");

            // 200 OK
            if (status == 200) {
                String body = decodeBody(resp);

                if (!looksLikeJson(ct.orElse(null), body)) {
                    throw new RuntimeException("Expected JSON but got Content-Type="
                            + ct.orElse("?") + " body(head)=" + headSnippet(body));
                }

                AnimeOpEd parsed = parseOneAnime(body);
                return Optional.ofNullable(parsed);
            }

            // 429 / 503 → バックオフしてリトライ
            if (status == 429 || status == 503) {
                if (attempt > maxRetries) {
                    String head = headSnippet(decodeBodyQuiet(resp));
                    throw new RuntimeException("Exceeded max retries: status=" + status + " body(head)=" + head);
                }

                long waitMs = safeIntervalMs * (1L << (attempt - 1));
                waitMs = Math.min(waitMs, 60_000L); // 念のため上限

                // X-RateLimit-Reset があるなら尊重
                try {
                    if (reset.isPresent()) {
                        long resetEpoch = Long.parseLong(reset.get());
                        long nowSec = System.currentTimeMillis() / 1000L;
                        long diff = resetEpoch - nowSec;
                        if (diff > 0) waitMs = Math.max(waitMs, diff * 1000L);
                    }
                } catch (NumberFormatException ignore) {}

                System.err.println("AnimeThemes API backoff: status=" + status + " elapsed=" + elapsed + "ms wait=" + waitMs + "ms attempt=" + attempt);
                Thread.sleep(waitMs);
                continue;
            }

            // その他
            throw new RuntimeException("HTTP error " + status + " body(head)=" + headSnippet(decodeBodyQuiet(resp)));
        }
    }

    private static AnimeOpEd parseOneAnime(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();

        JsonArray animes = Jsons.optArray(root, "anime");
        if (animes == null) animes = Jsons.optArray(root, "data");

        if (animes == null || animes.size() == 0) return null;

        JsonObject ao = animes.get(0).getAsJsonObject();
        String animeName = Jsons.optString(ao, "name", "(無題)");

        List<Theme> themes = new ArrayList<>();
        JsonArray tarr = Jsons.optArray(ao, "animethemes");
        if (tarr != null) {
            for (JsonElement tel : tarr) {
                JsonObject to = tel.getAsJsonObject();
                String type = Jsons.optString(to, "type", "?");

                JsonObject song = Jsons.optObj(to, "song");
                String songTitle = (song == null)
                        ? "(no song)"
                        : Jsons.optString(song, "title", "(untitled)");

                List<String> artists = new ArrayList<>();
                if (song != null) {
                    JsonArray art = Jsons.optArray(song, "artists");
                    if (art != null) {
                        for (JsonElement ar : art) {
                            String artistName = Jsons.optString(ar.getAsJsonObject(), "name", "");
                            if (!artistName.isBlank()) artists.add(artistName);
                        }
                    }
                }

                JsonArray entries = Jsons.optArray(to, "animethemeentries");
                int entryCount = (entries == null) ? 0 : entries.size();

                themes.add(new Theme(type, songTitle, artists, entryCount));
            }
        }

        return new AnimeOpEd(animeName, themes);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String decodeBody(HttpResponse<byte[]> resp) throws java.io.IOException {
        byte[] bytes = resp.body();
        Optional<String> ce = resp.headers().firstValue("Content-Encoding");

        boolean looksGzip = ce.orElse("").toLowerCase().contains("gzip")
                || (bytes != null && bytes.length >= 2
                && bytes[0] == (byte) 0x1f && bytes[1] == (byte) 0x8b);

        if (looksGzip) {
            try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(bytes));
                 InputStreamReader isr = new InputStreamReader(gis, StandardCharsets.UTF_8);
                 BufferedReader br = new BufferedReader(isr)) {
                StringBuilder sb = new StringBuilder();
                for (String line; (line = br.readLine()) != null; ) sb.append(line);
                return sb.toString();
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String decodeBodyQuiet(HttpResponse<byte[]> resp) {
        try {
            return decodeBody(resp);
        } catch (Exception e) {
            byte[] b = resp.body();
            int n = Math.min(64, b == null ? 0 : b.length);
            StringBuilder hex = new StringBuilder("0x");
            for (int i = 0; i < n; i++) hex.append(String.format("%02x", b[i]));
            return "[binary " + (b == null ? 0 : b.length) + " bytes head=" + hex + "]";
        }
    }

    private static boolean looksLikeJson(String contentType, String body) {
        if (contentType != null && contentType.toLowerCase().contains("json")) return true;
        String s = body.trim();
        return s.startsWith("{") || s.startsWith("[");
    }

    private static String headSnippet(String s) {
        if (s == null) return "";
        String head = s.substring(0, Math.min(200, s.length()));
        return head.replaceAll("\\s+", " ");
    }
}