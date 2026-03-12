package service.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import jakarta.servlet.http.HttpSession;

public class YouTubeTokenService {

	private final HttpClient http;

    public YouTubeTokenService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    public YouTubeTokenService(HttpClient http) {
        this.http = http;
    }

    private static final String KEY_ACCESS  = "yt_access_token";
    private static final String KEY_REFRESH = "yt_refresh_token";
    private static final String KEY_EXPIRES = "yt_expires_at";

    public String getValidAccessToken(HttpSession session) throws IOException {

        if (session == null) return null;

        synchronized (session) {

            Object atObj = session.getAttribute(KEY_ACCESS);
            Object expiresObj = session.getAttribute(KEY_EXPIRES);
            long now = System.currentTimeMillis();

            if (atObj instanceof String accessToken) {
                if (expiresObj instanceof Long expiresAt) {
                    if (now < expiresAt) {
                        return accessToken;
                    }
                } else {
                    return accessToken;
                }
            }

            Object refreshObj = session.getAttribute(KEY_REFRESH);
            if (!(refreshObj instanceof String refreshToken)) {
                return null;
            }

            String clientId = System.getenv("GOOGLE_CLIENT_ID");
            String clientSecret = System.getenv("GOOGLE_CLIENT_SECRET");

            if (clientId == null || clientSecret == null) {
                throw new IOException("OAuth client info not configured");
            }

            String body = "client_id=" + enc(clientId)
                    + "&client_secret=" + enc(clientSecret)
                    + "&refresh_token=" + enc(refreshToken)
                    + "&grant_type=refresh_token";

            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("https://oauth2.googleapis.com/token"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            try {
                HttpResponse<String> response =
                		http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    return null;
                }

                JsonObject o = JsonParser.parseString(response.body()).getAsJsonObject();
                if (!o.has("access_token")) return null;

                String newAccessToken = o.get("access_token").getAsString();
                session.setAttribute(KEY_ACCESS, newAccessToken);

                if (o.has("expires_in")) {
                    long expiresIn = o.get("expires_in").getAsLong();
                    long expiresAt = System.currentTimeMillis()
                            + expiresIn * 1000L - 60_000L;
                    session.setAttribute(KEY_EXPIRES, expiresAt);
                }

                return newAccessToken;

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while refreshing token", e);
            }
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}