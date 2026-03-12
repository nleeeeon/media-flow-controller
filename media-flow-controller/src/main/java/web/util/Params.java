package web.util;

import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

public class Params {
	public static String opt(HttpServletRequest req, String name) {
		String v = req.getParameter(name);
		return v == null ? "" : v.trim();
	}

	public static String opt(HttpServletRequest req, String name, String def) {
		String v = req.getParameter(name);
		v = (v == null ? "" : v.trim());
		return v.isEmpty() ? def : v;
	}

	public static int parseInt(String s, int def, int min, int max) {
		try {
			int v = Integer.parseInt(s);
			if (v < min) {
				v = min;
			}
			if (v > max) {
				v = max;
			}
			return v;
		} catch (Exception e) {
			return def;
		}
	}

	public static List<String> csv(HttpServletRequest req, String name) {
		String v = opt(req, name);
		List<String> out = new ArrayList<>();
		if (v.isEmpty()) {
			return out;
		}

		for (String s : v.split(",")) {
			if (s == null) {
				continue;

			}
			s = s.trim();
			if (!s.isEmpty()) {
				out.add(s);
			}
		}
		return out;
	}
}
