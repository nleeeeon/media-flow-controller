package web.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;

public class UrlUtil {
	public static String buildRedirectUri(HttpServletRequest req) {
		String env = System.getenv("OAUTH_REDIRECT_URI");
		if (env != null && !env.isBlank()) {
			return env;
		}

		return req.getScheme() + "://" + req.getServerName() + ":" + req.getServerPort()
				+ req.getContextPath() + "/auth/callback";
	}

	public static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
