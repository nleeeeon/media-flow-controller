// src/main/java/service/auth/GoogleOAuthService.java
package service.auth;

import dto.auth.GoogleTokenResponse;
import infrastructure.google.OAuthTokenClient;

public class GoogleOAuthService {

    private final OAuthTokenClient tokenClient = new OAuthTokenClient();

    public GoogleTokenResponse exchangeCodeForTokens(String code, String redirectUri) throws Exception {
        String clientId = System.getenv("GOOGLE_CLIENT_ID");
        String clientSecret = System.getenv("GOOGLE_CLIENT_SECRET");

        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLIENT_ID/SECRET is not configured");
        }

        return tokenClient.exchangeCode(code, redirectUri, clientId, clientSecret);
    }
}