// AiPlaylistServlet.java
package servlets.youtube.playlist; // ← あなたのパッケージ

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import dao.youtube.Search_keyword_DAO;
import dto.response.SearchKeywordResult;
import dto.youtube.MetaMini;
import dto.youtube.VideoInfo;
import infrastructure.youtube.YouTubeApiClient;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import service.auth.YouTubeTokenService;
import service.youtube.VideoSearchService;
import service.youtube.YoutubeMetaIngestService;
import util.string.Strings;
import util.youtube.YouTubeIdParser;
import web.util.Jsons;
import web.util.Responses;

@WebServlet("/youtube/playlist" )
public class YoutubePlaylistServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		Responses.prepareJson(resp);

		String q = Strings.opt(req.getParameter("q"), "").trim();
		if (q.isEmpty()) {
			Responses.writeErr(resp, "NO_QUERY", "q is required");
			return;
		}
		YouTubeTokenService ytYoken = new YouTubeTokenService();
		String accessToken = ytYoken.getValidAccessToken(req.getSession(false)); // nullなら未ログイン/未承認
		// プレイリストURL/ID → そのまま返す（API不要）
		String plId = YouTubeIdParser.detectPlaylistId(q);
		YoutubeMetaIngestService ytService = new YoutubeMetaIngestService();
		try {
			if (plId != null && accessToken != null)
				ytService.playlistVideoSave(accessToken, plId);
		} catch (Exception e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		if (plId != null) {
			JsonObject out = Jsons.ok();
			out.addProperty("mode", "playlist");
			out.addProperty("playlistId", plId);

			Responses.writeJson(resp, out);
			return;
		}

		// 動画URL/ID 羅列 → そのまま返す（API不要）
		LinkedHashSet<String> idsFromText = YouTubeIdParser.extractVideoIds(q);
		try {
			if (!idsFromText.isEmpty() && accessToken != null)
				ytService.videoSave(accessToken, idsFromText);
		} catch (Exception e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		if (!idsFromText.isEmpty()) {

			Responses.writeJson(resp, Jsons.okVideoIds(idsFromText, false));
			return;
		}

		// データベースからキャッシュ
		Search_keyword_DAO dao = new Search_keyword_DAO();
		String key = ("q:" + q).toLowerCase(Locale.ROOT);

		Gson gson = new Gson();
		SearchKeywordResult result = dao.find(key);
		if (result != null) {
			java.lang.reflect.Type T_LIST_STR = new TypeToken<List<String>>() {
			}.getType();
			List<String> restored = gson.fromJson(result.getValueText(), T_LIST_STR);
			Responses.writeJson(resp, Jsons.okVideoIds(restored, true));
			return;
		}


		if(accessToken == null) {
			Responses.writeJson(resp, Jsons.err("NOT_AUTH", "ログインが必要です"));
			return;
		}
		// IDを収集（TrySearchCandidatesはOAuth/キー両対応の想定）
		VideoSearchService pianoService = new VideoSearchService();

		List<VideoInfo> picks = pianoService.searchOneKeyword(q, accessToken, false);

		List<String> candidateIds = picks.stream()
				.map(p -> p.videoId)
				.toList();
		dao.save(key, candidateIds);

		if (candidateIds.isEmpty()) {
			JsonObject out = Jsons.ok();
			out.add("videoIds", new JsonArray());
			Responses.writeJson(resp, out);
			return;
		}

		YouTubeApiClient api = new YouTubeApiClient();
		Map<String, MetaMini> meta = api.fetchMinimal(candidateIds, accessToken);
		try {
			ytService.videoSave(accessToken, new LinkedHashSet<>(meta.keySet()));
		} catch (Exception e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}

		JsonObject out = Jsons.ok();
		out.add("videoIds", Jsons.toJsonArray(candidateIds));
		Responses.writeJson(resp, out);
	}


}
