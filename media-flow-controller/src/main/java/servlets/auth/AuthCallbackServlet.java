// src/main/java/servlets/auth/AuthCallbackServlet.java
package servlets.auth;

import java.io.IOException;

import dto.auth.GoogleTokenResponse;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import service.auth.GoogleOAuthService;
import service.auth.UserAuthService;
import web.util.UrlUtil;

@WebServlet("/auth/callback")
public class AuthCallbackServlet extends HttpServlet {

    private final GoogleOAuthService oauthService = new GoogleOAuthService();
    private final UserAuthService userAuthService = new UserAuthService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

        String code = req.getParameter("code");
        if (code == null || code.isBlank()) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "OAuth failed: missing code");
            return;
        }

        // 既存実装を流用（後で GoogleOAuthService 側に寄せてもOK）
        String redirectUri = UrlUtil.buildRedirectUri(req);

        try {
        	GoogleTokenResponse token = oauthService.exchangeCodeForTokens(code, redirectUri);

            HttpSession session = req.getSession(true);
            userAuthService.login(session, token);

            resp.sendRedirect(req.getContextPath() + "/");
        } catch (Exception e) {
            // ここはログ基盤があるなら logger に寄せる
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "OAuth callback failed");
        }
    }
}