package servlets.youtube.meta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonObject;

import dto.youtube.AgeCheckResult;
import infrastructure.youtube.YouTubeApiClient;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import web.util.Jsons;
import web.util.Responses;

@WebServlet("/youtube/ageCheck")
public class AgeCheckServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

    	Responses.prepareJson(resp);

        HttpSession sess = req.getSession(false);
        if (sess == null || sess.getAttribute("yt_access_token") == null) {
        	Responses.writeJson(resp, Jsons.notAuth(req.getContextPath() + "/auth/start"));
            return;
        }

        final String accessToken = (String) sess.getAttribute("yt_access_token");

        String idsCsv = Optional.ofNullable(req.getParameter("ids")).orElse("").trim();
        if (idsCsv.isEmpty()) {
        	JsonObject out = newEmbedCheckResult(null, null, null);

        	Responses.writeJson(resp, out);
            return;
        }

        List<String> ids = new ArrayList<>();
        for (String s : idsCsv.split(",")) if (!s.isBlank()) ids.add(s.trim());
        
        YouTubeApiClient api = new YouTubeApiClient();
        AgeCheckResult result = api.checkAgeRestriction(accessToken, ids);

        JsonObject out = newEmbedCheckResult(result.allowed, result.ageRestricted, result.notEmbeddable);

        Responses.writeJson(resp, out);
    }

    private JsonObject newEmbedCheckResult(
            List<String> allowed,
            List<String> ageRestricted,
            List<String> notEmbeddable) {

        JsonObject out = new JsonObject();
        out.add("allowed", Jsons.toJsonArray(allowed));
        out.add("ageRestricted", Jsons.toJsonArray(ageRestricted));
        out.add("notEmbeddable", Jsons.toJsonArray(notEmbeddable));
        return out;
    }
    
}
