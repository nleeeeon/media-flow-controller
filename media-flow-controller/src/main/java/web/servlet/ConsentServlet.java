package web.servlet;

import java.io.IOException;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/consent")
public class ConsentServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		String next = req.getParameter("next");
		if (next == null || next.isBlank()) {
			next = req.getContextPath() + "/";
		}

		resp.setContentType("text/html; charset=UTF-8");
		resp.getWriter().write("""
				  <!doctype html><html lang="ja"><body>
				  <h2>利用規約・プライバシーポリシーへの同意</h2>
				  <p><a href="./terms.html" target="_blank" rel="noopener">当サイト利用規約</a></p>
				  <p><a href="./privacy.html" target="_blank" rel="noopener">当サイトのプライバシーポリシー</a></p>
				  <form method="post">
				    <input type="hidden" name="next" value="%s">
				    <button type="submit">同意して続行</button>
				  </form>
				  </body></html>
				""".formatted(escapeHtml(next)));
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
		Cookie c = new Cookie("CONSENT", "1");
		c.setPath(req.getContextPath().isEmpty() ? "/" : req.getContextPath());
		c.setMaxAge(60 * 60 * 24 * 180);
		c.setHttpOnly(true);
		resp.addCookie(c);

		String next = req.getParameter("next");
		if (next == null || next.isBlank()) {
			next = req.getContextPath() + "/";

		}
		resp.sendRedirect(next);
	}

	private static String escapeHtml(String s) {
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&#39;");
	}
}
