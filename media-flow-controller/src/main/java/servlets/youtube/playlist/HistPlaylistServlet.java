package servlets.youtube.playlist;
// src/main/java/app/web/HistPlaylistServlet.java

import java.io.IOException;
import java.util.List;

import com.google.gson.JsonObject;

import dao.user.MusicArchiveDao;
import domain.youtube.VideoGenre;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import web.util.Jsons;
import web.util.Params;
import web.util.Responses;
import web.util.SessionUtil;

@WebServlet("/youtube/histPlaylist")
public class HistPlaylistServlet extends HttpServlet {
	private final MusicArchiveDao dao = new MusicArchiveDao();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		Responses.prepareJson(resp);

		Long userId = SessionUtil.getUserId(req);
		if (userId == null) {
			Responses.writeErr(resp, HttpServletResponse.SC_UNAUTHORIZED, "NOT_AUTH", "Invalid or missing userId.");
			return;
		}

		String fromYm = Params.opt(req, "fromYm");
		String toYm = Params.opt(req, "toYm");
		String mode = Params.opt(req, "mode", "count"); // count | random
		int limit = Params.parseInt(Params.opt(req, "limit", "50"), 50, 1, 200);
		String genreStr = Params.opt(req, "genre");

		VideoGenre genre;
		try {
			genre = VideoGenre.valueOf(genreStr);
		} catch (IllegalArgumentException e) {
			genre = null;
		}
		if (fromYm.isEmpty() || toYm.isEmpty() || fromYm.compareTo(toYm) > 0) {
			Responses.writeErr(resp, "BAD_REQUEST", "fromYm/toYm が不正です");
			return;
		}

		List<String> songKeys;
		try {
			if (genre != null && genre == VideoGenre.MAD) {

				if ("random".equalsIgnoreCase(mode)) {
					songKeys = dao.findRandomVideoIds(userId, fromYm, toYm, genre, limit);
				} else {
					songKeys = dao.findTopVideoIds(userId, fromYm, toYm, genre, limit);
				}

			} else {

				if ("random".equalsIgnoreCase(mode)) {
					songKeys = dao.findRandomVideoIds(userId, fromYm, toYm, genre, limit);
				} else {
					songKeys = dao.findTopVideoIds(userId, fromYm, toYm, genre, limit);
				}
			}

		} catch (Exception e) {
			Responses.writeErr(resp, "DB_ERROR", e.getMessage());
			return;
		}

		JsonObject out = Jsons.ok();
		out.addProperty("count", songKeys.size());
		out.add("ids", Jsons.toJsonArray(songKeys));

		Responses.writeJson(resp, out);
	}

}
