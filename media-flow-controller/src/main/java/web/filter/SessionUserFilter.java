package web.filter;

import java.io.IOException;

import dao.user.UsersDao;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@WebFilter("/*")
public class SessionUserFilter implements Filter {

	@Override
	public void doFilter(
			ServletRequest request,
			ServletResponse response,
			FilterChain chain)
			throws IOException, ServletException {

		HttpServletRequest req = (HttpServletRequest) request;

		HttpSession session = req.getSession(true);

		// まだ userId が無ければ「ゲスト用 userId」を付与
		if (session.getAttribute("userId") == null) {
			UsersDao dao = new UsersDao();
			long guestId = dao.findAnyUserId();
			session.setAttribute("userId", guestId);
		}

		chain.doFilter(request, response);
	}
}
