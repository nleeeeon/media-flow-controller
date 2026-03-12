package servlets.youtube.piano;

import java.io.IOException;
import java.sql.SQLException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import dao.music.Music_piano_video_dao;
import dao.youtube.Videos_dao;
import dto.music.MusicDetail;
import dto.music.MusicPianoVideo;
import dto.music.VideoRecord;
import dto.youtube.VideoidWithThumbnail;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import listener.AppStartupListener;
import service.auth.YouTubeTokenService;
import service.youtube.MusicIdentificationService;
import web.util.Params;
import web.util.Responses;

/**
 * Servlet implementation class PianoVideoGetServlet
 */
@WebServlet("/piano/getVideo")
public class PianoVideoGetServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
	private static final Gson G = new Gson();
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		Responses.prepareJson(resp);

    	String videoId = Params.opt(req, "videoId");
        if (videoId.isEmpty()) {
            Responses.writeErr(resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST",
                    "videoIdが送られていません");
            return;
        }
        Videos_dao vDao = new Videos_dao();
        VideoRecord m = null;
		try {//このtrycatchはあとで考える
			m = vDao.findByVideoId(videoId);
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		if(m == null) {
		    Responses.writeErr(resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST",
                    "videoIdからデータを取得できませんでした");
			return;
		}
		Music_piano_video_dao pDao = new Music_piano_video_dao();
		MusicPianoVideo piano = null;
		try {
			piano = pDao.findTopByMusicId(m.musicId());
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
		if(piano != null) {
			JsonObject out = new JsonObject();
		    out.addProperty("ok", true);
		    out.addProperty("pianoVideoId", piano.videoId());
		    out.addProperty("recordPianoVideo", true);
		    resp.getWriter().write(G.toJson(out));
			return;
		}
		
		MusicDetail musicDetail = AppStartupListener.musicIndexManager.getMusicDetail(m.musicId());
		String query = "";
		if(musicDetail.fixed() && musicDetail.artist().length()+musicDetail.song().length() >= 15) {
			query = musicDetail.song() + " " + "ピアノ";
		}else {
			query = musicDetail.artist() + " " + musicDetail.song() + " " + "ピアノ";
			
		}
		System.out.println("検索キーワード≗"+query);
		VideoidWithThumbnail result = new VideoidWithThumbnail();
		YouTubeTokenService ytTokenService = new YouTubeTokenService();
		String token = ytTokenService.getValidAccessToken(req.getSession(false));
	    if (token == null || token.isBlank()) {
	    	
		    Responses.writeErr(resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST",
                    "Googleで認可してください（yt_access_token がありません）");
	      return;
	    }
	    String pianoVideoId = null;
		try {
			result = MusicIdentificationService.searchVideo(musicDetail.artistKey(), musicDetail.songKey(), query, token, musicDetail.fixed());
			pianoVideoId = result.mainVideoId;
			System.out.println("検索が行われました");
		} catch (IOException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
			throw new ServletException(e);
		} catch (InterruptedException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
			throw new ServletException(e);
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
			throw new ServletException(e);
		}
		
		if(pianoVideoId == null) {
			
		    Responses.writeErr(resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST",
                    "多分該当のピアノ動画が存在しません");
		    return;
		}
		
		JsonObject out = new JsonObject();
	    out.addProperty("ok", true);
	    out.addProperty("pianoVideoId", pianoVideoId);
	    out.add("thumbnails", new Gson().toJsonTree(result.thumbnails));
	    resp.getWriter().write(G.toJson(out));
	}

}
