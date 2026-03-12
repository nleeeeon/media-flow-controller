package servlets.youtube.stats;
// src/main/java/app/web/SongStatsServlet.java

import java.io.IOException;
import java.sql.SQLException;

import com.google.gson.JsonObject;

import dao.user.MusicArchiveDao;
import dao.youtube.Videos_dao;
import dto.music.MusicDetail;
import dto.music.VideoRecord;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import listener.AppStartupListener;
import web.util.Params;
import web.util.Responses;

@WebServlet("/youtube/songStats")
public class SongStatsServlet extends HttpServlet {
	private final MusicArchiveDao dao = new MusicArchiveDao();

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		Responses.prepareJson(resp);

		Long userId = (Long) req.getSession().getAttribute("userId");
		// ★ 認証は既存方針に合わせてください（セッションや自前Authクラスなど）
		if (userId == null) {
			Responses.writeErr(resp, "NOT_AUTH", "ログインが必要です");
			return;
		}

		String videoId = Params.opt(req, "videoId");
		if (videoId.isEmpty()) {
			Responses.writeErr(resp, "BAD_REQUEST", "videoId は必須です");
			return;
		}

		// ▼ デバッグログ: videoId を出力
		System.out.println("[SongStatsServlet] user=" + userId + " videoId=" + videoId);

		String fromYm = Params.opt(req, "fromYm");
		String toYm = Params.opt(req, "toYm");

		// song_key = videoId として扱う（将来差が出るなら解決テーブルを挟む）
		String songKey = videoId;

		// 期間内
		MusicArchiveDao.Stats range = null;
		if (!fromYm.isEmpty() && !toYm.isEmpty() && fromYm.compareTo(toYm) <= 0) {
			range = dao.findStatsInRange(userId, songKey, fromYm, toYm);
		}

		// 累計
		MusicArchiveDao.Stats all = dao.findStatsAllTime(userId, songKey);

		JsonObject out = new JsonObject();
		out.addProperty("ok", true);
		out.addProperty("videoId", videoId);

		JsonObject r = new JsonObject();
		if (range != null) {
			r.addProperty("plays", range.plays());
			if (range.first() != null)
				r.addProperty("first", range.first().toString());
			if (range.last() != null)
				r.addProperty("last", range.last().toString());
		} else {
			r.addProperty("plays", 0);
		}
		out.add("range", r);

		JsonObject a = new JsonObject();
		a.addProperty("plays", all.plays());
		out.add("all", a);

		VideoRecord m = null;
		Videos_dao dao = new Videos_dao();
		try {//このtrycatchはあとで考える
			m = dao.findByVideoId(videoId);
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		if (m != null && m.musicId() != null) {
			MusicDetail music = AppStartupListener.musicIndexManager.getMusicDetail(m.musicId());

			out.addProperty("artist", music.artist());
			out.addProperty("song", music.song());

		}
		Responses.writeJson(resp, out);
	}

}
