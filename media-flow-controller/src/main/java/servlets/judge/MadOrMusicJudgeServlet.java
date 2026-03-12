package servlets.judge;

import java.io.IOException;
import java.sql.SQLException;

import com.google.gson.JsonObject;

import dao.youtube.Videos_dao;
import domain.youtube.VideoGenre;
import dto.music.VideoRecord;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import web.util.Jsons;
import web.util.Params;
import web.util.Responses;

@WebServlet("/video/judge")
public class MadOrMusicJudgeServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // ★ JSONレスポンス準備（UTF-8）
        Responses.prepareJson(resp);

        // ★ 安全なパラメータ取得
        String videoId = Params.opt(req, "videoId");

        if (videoId.isEmpty()) {
            Responses.writeErr(resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST",
                    "videoIdが送られていません");
            return;
        }

        // 判定処理（ビジネスロジック）
        VideoRecord result = null;
        Videos_dao dao = new Videos_dao();
		try {
			result = dao.findByVideoId(videoId);
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}

		//存在しなければ念のためにfalseを返す
        if (result == null || result.genre() == VideoGenre.MAD) {
            Responses.writeJson(resp, videoJudge(false, null));
            return;
        }

        // MADではない
        Responses.writeJson(resp, videoJudge(true, result.genre() == VideoGenre.PIANO));
    }
    private JsonObject videoJudge(boolean judge, Boolean isPiano){
        JsonObject root = Jsons.ok();
        root.addProperty("judge", judge);

        // isPiano は judge=true の時だけ付ける
        if (judge && isPiano != null) {
            root.addProperty("isPiano", isPiano);
        }
        return root;
    }
}