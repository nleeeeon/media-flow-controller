package servlets.youtube.meta;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import dao.youtube.Videos_dao;
import dto.music.VideoRecord;
import dto.youtube.Thumb;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import web.util.Jsons;
import web.util.Params;
import web.util.Responses;

@WebServlet("/playlist/thumbs")
public class PlaylistThumbsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Responses.prepareJson(resp);

        // カンマ区切りパース（Paramsに csv() を追加するならそっちに寄せてもOK）
        List<String> ids = Params.csv(req, "ids");

        if (ids.isEmpty()) {
            Responses.writeErr(resp, "NO_VALID_IDS", "有効な videoId が含まれていません。");
            return;
        }

        Map<String, Thumb> map = new HashMap<>();
        Videos_dao vDao = new Videos_dao();
        Map<String, VideoRecord> videoRecords = new HashMap<>();
		try {
			videoRecords = vDao.findByVideoIds(ids);
		} catch (SQLException e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
        for(Map.Entry<String, VideoRecord> entry : videoRecords.entrySet()) {
        	map.put(entry.getKey(), new Thumb(entry.getKey(), entry.getValue().thumbnailUrl(), entry.getValue().videoTitle()));
        }
        
        JsonObject root = Jsons.ok();
        JsonArray items = new JsonArray();

        if (ids != null) {
            for (String id : ids) {
                JsonObject item = new JsonObject();
                item.addProperty("videoId", id);

                Thumb t = (map == null) ? null : map.get(id);
                if (t != null) {
                    if (t.thumbnailUrl != null) item.addProperty("thumbnail", t.thumbnailUrl);
                    else item.add("thumbnail", JsonNull.INSTANCE);

                    if (t.videoTitle != null) item.addProperty("title", t.videoTitle);
                    else item.add("title", JsonNull.INSTANCE);
                } else {
                    item.add("thumbnail", JsonNull.INSTANCE);
                    item.add("title", JsonNull.INSTANCE);
                }

                items.add(item);
            }
        }

        root.add("items", items);
        Responses.writeJson(resp, root);
        

        
    }
}