// src/main/java/service/auth/UserAuthService.java
package service.auth;

import com.google.gson.JsonObject;

import dto.auth.GoogleTokenResponse;
import jakarta.servlet.http.HttpSession;

public class UserAuthService {

    private final IdTokenParser idTokenParser = new IdTokenParser();

    public void login(HttpSession session, GoogleTokenResponse token) {

        // 1) Token保存
        session.setAttribute("yt_access_token", token.accessToken());

        if (token.refreshToken() != null && !token.refreshToken().isBlank()) {
            session.setAttribute("yt_refresh_token", token.refreshToken());
        }

        // 期限を保存（1分早めに切る）
        if (token.expiresInSec() != null) {
            long expiresAt = System.currentTimeMillis() + token.expiresInSec() * 1000L - 60_000L;
            session.setAttribute("yt_expires_at", expiresAt);
        }

        // 2) id_token から sub 抽出 → googleSub 保存
        String sub = idTokenParser.extractSub(token.idToken());
        session.setAttribute("googleSub", sub);

        // 3) DB紐付け（userId確定）
        long uid = new dao.user.UsersDao().ensureUidForSub(sub);
        session.setAttribute("userId", uid);

        // 4) 表示用（任意）
        JsonObject payload = idTokenParser.parsePayload(token.idToken());
        if (payload.has("email"))   session.setAttribute("googleEmail", payload.get("email").getAsString());
        if (payload.has("name"))    session.setAttribute("googleName", payload.get("name").getAsString());
        if (payload.has("picture")) session.setAttribute("googlePicture", payload.get("picture").getAsString());
    }
}