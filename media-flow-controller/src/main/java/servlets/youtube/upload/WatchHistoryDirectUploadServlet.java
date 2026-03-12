package servlets.youtube.upload;

import java.io.IOException;
import java.io.InputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import service.auth.YouTubeTokenService;
import service.youtube.WatchHistoryUploadService;
import util.common.StreamUtils;
import web.util.Responses;
import web.util.SessionUtil;

/**
 * POST /api/upload/watch-history
 *  - multipart/form-data で "file" に watch-history.json(.gz可) をアップロード
 *  - サーバ側で 3 つに分割し、同一アプリの
 *      /api/upload/profile30
 *      /api/upload/music
 *      /api/upload/nonmusic
 *    へ JSON を POST する（JSESSIONID を引き継ぐ）
 */
@WebServlet("/api/upload/watch-history")
@MultipartConfig(fileSizeThreshold = 1 * 1024 * 1024, maxFileSize = 200 * 1024 * 1024, maxRequestSize = 200 * 1024
		* 1024)
public class WatchHistoryDirectUploadServlet extends HttpServlet {

	/*private static final String CHANNELS_LIST_ENDPOINT =
		    "https://www.googleapis.com/youtube/v3/channels?part=statistics&id=";*/
	//旧: part=statistics だけ → 新: snippet,localizations もまとめて取得（日本語優先）

	//日本語タイトル（あれば）、無ければデフォルトを入れる

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
		Responses.prepareJson(resp);
		// 1) 前提: OAuth アクセストークン（セッション保管済み想定）
		YouTubeTokenService ytTokenService = new YouTubeTokenService();
		String token = ytTokenService.getValidAccessToken(req.getSession(false));
		if (token == null || token.isBlank()) {
			Responses.writeErr(resp, "NOT_AUTH", "Googleで認可してください（yt_access_token がありません）");
			return;
		}

		Long userId = SessionUtil.getUserId(req);
		if (userId == null) {
			Responses.writeErr(resp, "NOT_AUTH", "ログインしてください（userId がありません）");
			return;
		}
		Part part = null;
		part = req.getPart("file");
		if (part == null) {
			resp.sendError(400, "file part is required");
			return;
		}

		try (InputStream raw = part.getInputStream();
				InputStream in = StreamUtils.wrapGzipIfNeeded(raw)) {
			WatchHistoryUploadService service = new WatchHistoryUploadService();
			boolean result = service.processWatchHistoryUpload(in, token, userId);
			if (result) {
				Responses.writeUploadOk(resp, true, "解析が完了しました");
				return;
			} else {
				Responses.writeUploadOk(resp, false, "音楽として採用された動画がありません（しきい値/ルールを見直してください）");
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
			Responses.writeErr(resp, "BAD_REQUEST", e.getMessage());
			return;
		}

	}

}
