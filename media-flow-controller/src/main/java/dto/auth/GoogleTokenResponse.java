// src/main/java/dto/auth/TokenResponse.java
package dto.auth;

import com.google.gson.JsonObject;

public record GoogleTokenResponse(
        String accessToken,
        String refreshToken,
        String idToken,
        Long expiresInSec
) {
    public static GoogleTokenResponse fromJson(JsonObject o) {
        String access = getString(o, "access_token", true);
        String refresh = getString(o, "refresh_token", false);
        String idToken = getString(o, "id_token", true); // あなたの実装では sub 抽出に使っているため必須扱い
        Long expires = getLong(o, "expires_in", false);
        return new GoogleTokenResponse(access, refresh, idToken, expires);
    }

    private static String getString(JsonObject o, String key, boolean required) {
        if (o.has(key) && !o.get(key).isJsonNull()) {
            return o.get(key).getAsString();
        }
        if (required) throw new IllegalArgumentException("Missing field: " + key);
        return null;
    }

    private static Long getLong(JsonObject o, String key, boolean required) {
        if (o.has(key) && !o.get(key).isJsonNull()) {
            return o.get(key).getAsLong();
        }
        if (required) throw new IllegalArgumentException("Missing field: " + key);
        return null;
    }
}