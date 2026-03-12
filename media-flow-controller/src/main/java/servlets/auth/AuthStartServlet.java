// src/main/java/example/AuthStartServlet.java
package servlets.auth;

import java.io.IOException;
import java.net.URLEncoder;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import web.util.UrlUtil;

@WebServlet("/auth/start")
public class AuthStartServlet extends HttpServlet {
  private static final String AUTH = "https://accounts.google.com/o/oauth2/v2/auth";
  private static final String SCOPE = String.join(" ",
		    "https://www.googleapis.com/auth/youtube.readonly"
		    //"https://www.googleapis.com/auth/youtube",YouTubeの公式のほうにプレイリストを保存する時に必要
		    //"https://www.googleapis.com/auth/userinfo.email",   // 任意
		    //"https://www.googleapis.com/auth/userinfo.profile"  // 任意
		);
		// 認可URL生成時に &scope=... をこれで


  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
	  if(!System.getenv().getOrDefault("DB_URL","").equals(""))return;
      String clientId = System.getenv("GOOGLE_CLIENT_ID");
      if (clientId == null || clientId.isBlank()) {
          resp.sendError(500, "Missing env GOOGLE_CLIENT_ID. Set it in Run Configurations > Environment.");
          return;
      }

      String redirect = UrlUtil.buildRedirectUri(req);
      System.out.println("[auth/start] redirect = " + redirect); // デバッグ用

      String force = req.getParameter("force");
      boolean forceConsent = "1".equals(force) || "true".equalsIgnoreCase(force);

      String url = AUTH
        + "?client_id=" + URLEncoder.encode(clientId, java.nio.charset.StandardCharsets.UTF_8)
        + "&redirect_uri=" + URLEncoder.encode(redirect, java.nio.charset.StandardCharsets.UTF_8)
        + "&response_type=code"
        + "&scope=" + URLEncoder.encode(SCOPE, java.nio.charset.StandardCharsets.UTF_8)
        + "&access_type=offline"
        + "&include_granted_scopes=true"
        + (forceConsent ? "&prompt=consent" : "");

      resp.sendRedirect(url);
  }

  

}
