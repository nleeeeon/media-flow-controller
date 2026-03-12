package web.util;

import jakarta.servlet.http.HttpServletRequest;

public class SessionUtil {
	public static Long getUserId(HttpServletRequest req) {
		Object u = req.getSession().getAttribute("userId");
		if (u == null) {
			return null;
		}
		if (u instanceof Long l) {
			return l;
		}
		if (u instanceof Integer i) {
			return i.longValue();

		}
		return Long.parseLong(String.valueOf(u));
	}
}
