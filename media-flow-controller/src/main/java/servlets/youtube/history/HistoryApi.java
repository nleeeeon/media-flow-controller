package servlets.youtube.history;

import java.io.IOException;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import dao.youtube.Search_keyword_DAO;
import dto.response.SearchKeywordResult;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import web.util.Jsons;
import web.util.Responses;

@WebServlet("/api/history")
public class HistoryApi extends HttpServlet {

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {

    Responses.prepareJson(resp);

    try {
      List<SearchKeywordResult> list = new Search_keyword_DAO().allFind(0, 50);

      JsonObject root = Jsons.ok();
      JsonArray arr = new JsonArray();

      if (list != null) {
          for (SearchKeywordResult h : list) {
              JsonObject obj = new JsonObject();

              // cache_key
              String key = null;
              if (h.getValueText() != null && h.getValueText().length() >= 2) {
                  key = h.getValueText().substring(2);
              }
              obj.addProperty("cache_key", key);

              // created_at
              if (h.getCreatedAt() != null) {
                  obj.addProperty("created_at", h.getCreatedAt().toString());
              } else {
                  obj.add("created_at", JsonNull.INSTANCE);
              }

              arr.add(obj);
          }
      }

      root.add("items", arr);
      
      Responses.writeJson(resp, root);

    } catch (Exception e) {
      e.printStackTrace();
      Responses.writeErr(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
              "INTERNAL_ERROR", "履歴の取得に失敗しました");
    }
  }
}