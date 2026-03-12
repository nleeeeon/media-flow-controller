package servlets.youtube.piano;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import dao.music.Music_piano_video_dao;
import dao.youtube.Videos_dao;
import dto.music.MusicPianoVideo;
import dto.music.VideoRecord;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import web.util.Jsons;
import web.util.Params;
import web.util.Responses;

/**
 * Servlet implementation class PianoCandidatesServlet
 */
@WebServlet("/piano/candidates")
public class PianoCandidatesServlet extends HttpServlet {


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

    	Responses.prepareJson(resp);

    	String videoId = Params.opt(req, "videoId");
        if (videoId.isEmpty()) {
            Responses.writeErr(resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST",
                    "videoIdが送られていません");
            return;
        }

        // 1) 動画ID → 音楽データ
        Videos_dao vDao = new Videos_dao();
        VideoRecord m = null;
		try {
			m = vDao.findByVideoId(videoId);
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
        if (m == null || m.musicId() == null) {
        	Responses.writeJson(resp, Jsons.ok("MAD"));
            return;
        }


        // 3) ピアノ対応情報を取得
        Music_piano_video_dao pDao = new Music_piano_video_dao();
        List<MusicPianoVideo> pianos = null;
		try {
			pianos = pDao.findByMusicIdOrderByConfidenceDesc(m.musicId());
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}

        if (pianos == null || pianos.isEmpty()) {
        	JsonObject o = Jsons.ok();
            o.add("piano", new JsonObject());
            return;
        }

        JsonArray arr = new JsonArray();
        Set<String> videoIds = new HashSet<>();

        for (MusicPianoVideo p : pianos) {
            videoIds.add(p.videoId());
        }
        Map<String, VideoRecord> videoRecords = new HashMap<>();
        
        try {
			videoRecords = vDao.findByVideoIds(videoIds);
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
        
        for(Map.Entry<String, VideoRecord> entry : videoRecords.entrySet()) {
        	JsonObject o = new JsonObject();
            o.addProperty("videoId", entry.getKey());
            o.addProperty("channelId", entry.getValue().channelId());
            o.addProperty("thumbnail", entry.getValue().thumbnailUrl());
            o.addProperty("title", entry.getValue().videoTitle());
            arr.add(o);
        }
        
        

        JsonObject out = new JsonObject();
        out.addProperty("ok", true);
        out.add("piano", arr);
        resp.getWriter().write(out.toString());
    }
}
