package servlets.segments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dao.user.UserSegmentsJsonDao;
import dao.user.UserSegmentsJsonDao.SegmentData;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import web.util.Jsons;
import web.util.Responses;
import web.util.SessionUtil;

@WebServlet("/segments")
public class SegmentsServlet extends HttpServlet {

    private static final Gson G = new Gson();
    private transient UserSegmentsJsonDao dao;

    @Override
    public void init() throws ServletException {
        dao = new UserSegmentsJsonDao();
    }
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
    	Responses.prepareJson(resp);

        String videoId = req.getParameter("videoId");
        if (videoId == null || videoId.isBlank()) {
        	Responses.writeErr(resp,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "BAD_REQUEST",
                    "videoIdが送られていません");
            return;
        }

        Long userId = SessionUtil.getUserId(req);
		if (userId == null) {
			Responses.writeErr(resp, HttpServletResponse.SC_UNAUTHORIZED, "NOT_AUTH", "Invalid or missing userId.");
			return;
		}

        // 種類（type）を取得 ― 指定なしなら default
        String type = Optional.ofNullable(req.getParameter("type"))
                              .filter(s -> !s.isBlank())
                              .orElse("default");

        // DB からすべての種類をまとめて取得 (type → SegmentData)
        Map<String, SegmentData> all = dao.loadJson(userId, videoId);

        // タイプ一覧
        List<String> types = new ArrayList<>(all.keySet());
        Collections.sort(types);

        // 指定タイプが DB に無い場合 → 空扱い
        SegmentData sd = all.get(type);

        JsonArray segsJson;
        int startSec;

        if (sd == null) {
            // デフォルトの空データ
            segsJson = new JsonArray();
            startSec = 0;
        } else {
            // JSONをパース
            JsonObject root = JsonParser.parseString(sd.jsonText()).getAsJsonObject();
            segsJson = root.has("segments") ? root.getAsJsonArray("segments") : new JsonArray();
            startSec = (sd.startSec() != null ? sd.startSec() : 0);
        }

        // ---- 返却 JSON ----
        JsonObject out = Jsons.ok();
        out.add("types", G.toJsonTree(types));
        out.addProperty("selectedType", type);
        out.addProperty("startSec", startSec);
        out.add("segments", segsJson);

        Responses.writeJson(resp, out);
    }



    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
    	Responses.prepareJson(resp);
    	Long userId = SessionUtil.getUserId(req);
		if (userId == null) {
			Responses.writeErr(resp, HttpServletResponse.SC_UNAUTHORIZED, "NOT_AUTH", "Invalid or missing userId.");
			return;
		}

        JsonObject body = JsonParser.parseReader(req.getReader()).getAsJsonObject();

        String videoId = body.has("videoId") ? body.get("videoId").getAsString() : null;
        if (videoId == null || videoId.isBlank()) {
        	Responses.writeErr(resp, "BAD_REQUEST", "videoId は必須です");
            return;
        }

        // type 指定なし → default
        String type = body.has("type") && !body.get("type").getAsString().isBlank()
                ? body.get("type").getAsString()
                : "default";

        // 開始位置
        int startSec = body.has("startSec") ? body.get("startSec").getAsInt() : 0;

        // segments の sanitize
        JsonArray inArr = body.has("segments") ? body.getAsJsonArray("segments") : new JsonArray();
        JsonArray outArr = new JsonArray();

        for (var el : inArr) {
            var o = el.getAsJsonObject();
            double s = o.get("start").getAsDouble();
            double e = o.get("end").getAsDouble();
            if (e > s) {
                JsonObject no = new JsonObject();
                no.addProperty("start", s);
                no.addProperty("end", e);
                no.addProperty("reason",
                        o.has("reason") ? o.get("reason").getAsString() : "user");
                outArr.add(no);
            }
        }

        // 保存用 JSON
        JsonObject root = new JsonObject();
        root.addProperty("source", "user");
        root.add("segments", outArr);

        String jsonText = root.toString();

        // ---- 保存 ----
        dao.upsertJson(userId, videoId, type, jsonText, startSec);

        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
