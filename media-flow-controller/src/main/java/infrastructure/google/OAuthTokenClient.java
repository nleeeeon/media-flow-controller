// src/main/java/client/google/OAuthTokenClient.java
package infrastructure.google;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dto.auth.GoogleTokenResponse;
import web.util.UrlUtil;

public class OAuthTokenClient {

    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final URI TOKEN_ENDPOINT = URI.create("https://oauth2.googleapis.com/token");

    public GoogleTokenResponse exchangeCode(
            String code,
            String redirectUri,
            String clientId,
            String clientSecret
    ) throws IOException, InterruptedException {

        String body = "code=" + UrlUtil.enc(code)
                + "&client_id=" + UrlUtil.enc(clientId)
                + "&client_secret=" + UrlUtil.enc(clientSecret)
                + "&redirect_uri=" + UrlUtil.enc(redirectUri)
                + "&grant_type=authorization_code";

        HttpRequest req = HttpRequest.newBuilder(TOKEN_ENDPOINT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() / 100 != 2) {
            // body をそのまま返すのは情報漏えいになる場合があるので、ログにだけ出す想定
            throw new IOException("Token endpoint returned " + res.statusCode());
        }

        JsonObject o = JsonParser.parseString(res.body()).getAsJsonObject();
        return GoogleTokenResponse.fromJson(o);
    }

}