package web.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebFilter("/*")
public class ConsentFilter implements Filter {

	private static boolean hasConsent(HttpServletRequest req) {
		Cookie[] cs = req.getCookies();
		if (cs == null) {
			return false;
		}
		for (Cookie c : cs) {
			if ("CONSENT".equals(c.getName()) && "1".equals(c.getValue())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;

		String path = req.getRequestURI().substring(req.getContextPath().length());

		// 除外（同意ページ自体・静的ページ・静的資産・APIの一部など）
		if (path.equals("/consent")
				|| path.equals("/privacy.html")
				|| path.equals("/terms.html")
				|| path.startsWith("/assets/")
				|| path.startsWith("/css/")
				|| path.startsWith("/js/")) {
			chain.doFilter(request, response);
			return;
		}

		if (!hasConsent(req)) {
			// 元のURLに戻れるように next を付ける
			String next = req.getRequestURI();
			if (req.getQueryString() != null) {
				next += "?" + req.getQueryString();
			}
			resp.sendRedirect(req.getContextPath() + "/consent?next=" +
					java.net.URLEncoder.encode(next, java.nio.charset.StandardCharsets.UTF_8));
			return;
		}

		chain.doFilter(request, response);
	}
}
